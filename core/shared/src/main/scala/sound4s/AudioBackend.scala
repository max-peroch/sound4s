package sound4s

import fs2.Stream

/** What a platform provides to capture audio. [[Microphone]] is built on it. */
private[sound4s] trait AudioCapture[F[_]] {
  def format: AudioFormat

  /** Reads captured PCM in chunks of `chunkSize` bytes, including any audio the
    * device buffered before reading began.
    */
  def read(chunkSize: Int): Stream[F, Byte]

  /** Discards the audio the device has buffered. */
  def flush: F[Unit]
}

/** What a platform provides to play audio. [[Speakers]] is built on it. */
private[sound4s] trait AudioPlayback[F[_]] {
  def format: AudioFormat

  /** Plays the first `length` bytes of `bytes`, returning once they are
    * accepted by the device.
    */
  def write(bytes: Array[Byte], length: Int): F[Unit]

  /** Discards the audio queued in the device and not yet played. */
  def flush: F[Unit]
}
