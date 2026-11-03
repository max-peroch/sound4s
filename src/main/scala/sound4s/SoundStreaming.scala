package sound4s

import cats.effect.Sync
import cats.effect.kernel.Resource
import cats.syntax.all.*
import org.typelevel.log4cats.Logger

import scala.concurrent.duration.*

import javax.sound.sampled.{
  AudioFormat,
  AudioInputStream,
  Mixer,
  SourceDataLine,
  TargetDataLine
}

/** A microphone and speakers opened together, chunked to a common frame size so
  * `source` output can be piped straight into `sink` (e.g. for loopback or RTP
  * relaying).
  *
  * Instances are only obtained via [[SoundStreaming.resource]].
  */
final class SoundStreaming[F[_]: Sync] private[sound4s] (
    private val sourceDataLine: SourceDataLine,
    private val targetDataLine: TargetDataLine,
    rtpPacketInterval: FiniteDuration
) {

  private val chunkSize: Int = {
    val audioFormat = targetDataLine.getFormat
    (audioFormat.getSampleRate * rtpPacketInterval.toUnit(
      SECONDS
    )).toInt * audioFormat.getFrameSize
  }

  /** Raw PCM captured from the microphone, in `chunkSize`-byte chunks.
    *
    * The line records from the moment it is opened, into a buffer of its own
    * (500 ms for 8 kHz, 16-bit mono on macOS) that nobody empties until this
    * stream is read. What it holds then is old, and sent on as it is, it is
    * heard late. So reading starts by flushing the line: the audio is the audio
    * from now.
    */
  def source: fs2.Stream[F, Byte] =
    fs2.Stream.exec(Sync[F].delay(targetDataLine.flush())) ++
      fs2.io.readInputStream(
        Sync[F].delay(new AudioInputStream(targetDataLine)),
        chunkSize,
        false
      )

  /** Writes raw PCM to the speakers, discarding any trailing bytes that don't
    * fill a whole frame.
    *
    * Written as fast as the line takes it, audio queues in the line (up to 500
    * ms for 8 kHz, 16-bit mono on macOS). A sink that ends plays that to the
    * end. One that is cancelled or fails drops it: the audio was given up, and
    * left to play it would put what is written next behind it, as a delay that
    * stays.
    */
  def sink: fs2.Pipe[F, Byte, Unit] =
    _.chunkN(chunkSize)
      .evalMap(write)
      .onFinalizeCase(exit =>
        dropQueued.unlessA(exit == Resource.ExitCase.Succeeded)
      )

  /** Drops what is queued in the speaker line: audio that was given up on. */
  private val dropQueued: F[Unit] = Sync[F].delay(sourceDataLine.flush())

  /** Writes the whole frames of `chunk`, and none of a trailing part of one. */
  private def write(chunk: fs2.Chunk[Byte]): F[Unit] = {
    val bytes  = chunk.toArray
    val usable =
      bytes.length - bytes.length % sourceDataLine.getFormat.getFrameSize
    Sync[F]
      .blocking {
        sourceDataLine.write(bytes, 0, usable)
        ()
      }
      .whenA(usable > 0)
  }
}

object SoundStreaming {

  private val DefaultRtpPacketInterval: FiniteDuration = 20.millis

  /** Opens the microphone and speakers together on the same mixer.
    *
    * @param audioFormat
    *   the PCM format for both capture and playback
    * @param logger
    *   receives an info message as each line is opened and closed
    * @param mixerInfo
    *   the mixer to use, as returned by [[MixerLookup.findByName]]; `None` uses
    *   the system default mixer
    * @param rtpPacketInterval
    *   the packet duration used to size `source`/`sink` chunks; defaults to 20
    *   ms
    * @return
    *   fails with [[AudioError.UnexpectedLineType]] if the mixer doesn't expose
    *   the expected line types for this format
    */
  def resource[F[_]: Sync](
      audioFormat: AudioFormat,
      logger: Logger[F],
      mixerInfo: Option[Mixer.Info] = None,
      rtpPacketInterval: FiniteDuration = DefaultRtpPacketInterval
  ): Resource[F, SoundStreaming[F]] =
    for {
      mixer <- MixerLookup.resource(mixerInfo, logger)
      sdl   <- Speakers.openSourceDataLine(mixer, audioFormat, logger)
      tdl   <- Microphone.openTargetDataLine(mixer, audioFormat, logger)
    } yield new SoundStreaming(sdl, tdl, rtpPacketInterval)
}
