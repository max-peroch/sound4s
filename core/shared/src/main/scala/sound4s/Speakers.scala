package sound4s

import cats.Applicative
import cats.effect.kernel.Resource
import cats.syntax.all.*
import fs2.{Chunk, Pipe}

/** Open speakers, obtained via [[Speakers.resource]]. */
final class Speakers[F[_]: Applicative] private[sound4s] (
    playback: AudioPlayback[F]
) {

  /** The PCM format these speakers play. */
  def format: AudioFormat = playback.format

  /** Writes raw PCM to the speakers, in chunks of
    * [[AudioFormat.DefaultPacketInterval]].
    */
  def sink: Pipe[F, Byte, Unit] = sink(
    format.chunkSize(AudioFormat.DefaultPacketInterval)
  )

  /** Writes raw PCM to the speakers in `chunkSize`-byte writes, discarding any
    * trailing bytes that don't fill a whole frame.
    *
    * Written as fast as the device takes it, audio queues in the device (up to
    * 500 ms for 8 kHz, 16-bit mono on macOS). A sink that ends plays that to
    * the end. One that is cancelled or fails drops it: the audio was given up,
    * and left to play it would put what is written next behind it, as a delay
    * that stays.
    */
  def sink(chunkSize: Int): Pipe[F, Byte, Unit] =
    _.chunkN(chunkSize)
      .evalMap(write)
      .onFinalizeCase(exit =>
        playback.flush.unlessA(exit == Resource.ExitCase.Succeeded)
      )

  /** Writes the whole frames of `chunk`, and none of a trailing part of one. */
  private def write(chunk: Chunk[Byte]): F[Unit] = {
    val bytes  = chunk.toArray
    val usable = bytes.length - bytes.length % format.frameSize
    playback.write(bytes, usable).whenA(usable > 0)
  }
}

object Speakers extends SpeakersCompanionPlatform
