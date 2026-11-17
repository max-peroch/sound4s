package sound4s

import scala.concurrent.duration.*

/** A PCM audio format, independent of the platform that captures or plays it.
  *
  * @param sampleRate
  *   samples per second, per channel (e.g. 8000)
  * @param bitDepth
  *   bits per sample (e.g. 16)
  * @param channels
  *   number of interleaved channels (1 for mono)
  * @param signed
  *   whether samples are signed
  * @param bigEndian
  *   whether multi-byte samples are big-endian
  */
final case class AudioFormat(
    sampleRate: Int,
    bitDepth: Int,
    channels: Int,
    signed: Boolean = true,
    bigEndian: Boolean = false
) {
  require(sampleRate > 0, s"sampleRate must be positive, got $sampleRate")
  require(bitDepth > 0, s"bitDepth must be positive, got $bitDepth")
  require(channels > 0, s"channels must be positive, got $channels")

  /** The number of bytes in one sample frame: one sample for every channel. */
  def frameSize: Int = channels * ((bitDepth + 7) / 8)

  /** The number of bytes holding `interval` of audio in this format, a whole
    * number of frames.
    */
  def chunkSize(interval: FiniteDuration): Int =
    (sampleRate * interval.toUnit(SECONDS)).toInt * frameSize
}

object AudioFormat {

  /** The packet duration used to size chunks when none is given: 20 ms, a
    * typical RTP packet.
    */
  val DefaultPacketInterval: FiniteDuration = 20.millis
}
