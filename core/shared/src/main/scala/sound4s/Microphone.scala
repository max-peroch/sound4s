package sound4s

import fs2.Stream

/** An open microphone, obtained via [[Microphone.resource]]. */
final class Microphone[F[_]] private[sound4s] (capture: AudioCapture[F]) {

  /** The PCM format this microphone captures in. */
  def format: AudioFormat = capture.format

  /** Raw PCM captured from the microphone, in chunks of
    * [[AudioFormat.DefaultPacketInterval]].
    */
  def source: Stream[F, Byte] = source(
    format.chunkSize(AudioFormat.DefaultPacketInterval)
  )

  /** Raw PCM captured from the microphone, in `chunkSize`-byte chunks.
    *
    * The device records from the moment it is opened, into a buffer of its own
    * (500 ms for 8 kHz, 16-bit mono on macOS) that nobody empties until this
    * stream is read. What it holds then is old, and sent on as it is, it is
    * heard late. So reading starts by flushing the device: the audio is the
    * audio from now.
    */
  def source(chunkSize: Int): Stream[F, Byte] =
    Stream.exec(capture.flush) ++ capture.read(chunkSize)
}

object Microphone extends MicrophoneCompanionPlatform
