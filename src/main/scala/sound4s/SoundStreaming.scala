package sound4s

import cats.effect.Sync
import cats.effect.kernel.Resource
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

  /** Raw PCM captured from the microphone, in `chunkSize`-byte chunks. */
  def source: fs2.Stream[F, Byte] =
    fs2.io.readInputStream(
      Sync[F].delay(new AudioInputStream(targetDataLine)),
      chunkSize,
      false
    )

  /** Writes raw PCM to the speakers, discarding any trailing bytes that don't
    * fill a whole frame.
    */
  def sink: fs2.Pipe[F, Byte, Unit] = stream =>
    stream
      .chunkN(chunkSize)
      .evalMap { chunk =>
        val byteArray = chunk.toArray
        val frameSize = sourceDataLine.getFormat.getFrameSize
        val usable    = byteArray.length - (byteArray.length % frameSize)
        if (usable > 0)
          Sync[F].blocking {
            sourceDataLine.write(byteArray, 0, usable)
            ()
          }
        else Sync[F].unit
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
