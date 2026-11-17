package sound4s

import scala.scalajs.js.typedarray.Float32Array

/** Converts between PCM bytes and the 32-bit floats Web Audio works in.
  *
  * Supports 8-bit unsigned and 16, 24 and 32-bit signed little-endian samples.
  */
private[sound4s] object PcmConversion {

  /** Whether `format` is one this conversion handles. */
  def supports(format: AudioFormat): Boolean =
    !format.bigEndian && (
      (format.bitDepth == 8 && !format.signed) ||
        (format.signed && Set(16, 24, 32).contains(format.bitDepth))
    )

  /** The first `length` bytes of `bytes` as interleaved floats in -1 to 1. */
  def toFloats(
      bytes: Array[Byte],
      length: Int,
      format: AudioFormat
  ): Float32Array = {
    val width   = format.bitDepth / 8
    val samples = length / width
    val floats  = new Float32Array(samples)
    var i       = 0
    while (i < samples) {
      val at    = i * width
      val value = format.bitDepth match {
        case 8  => ((bytes(at) & 0xff) - 128) / 128.0
        case 16 =>
          ((bytes(at) & 0xff) | (bytes(at + 1) << 8)) / 32768.0
        case 24 =>
          ((bytes(at) & 0xff) | ((bytes(at + 1) & 0xff) << 8) |
            (bytes(at + 2) << 16)) / 8388608.0
        case _ =>
          ((bytes(at) & 0xff) | ((bytes(at + 1) & 0xff) << 8) |
            ((bytes(at + 2) & 0xff) << 16) |
            (bytes(at + 3) << 24)) / 2147483648.0
      }
      floats(i) = value.toFloat
      i += 1
    }
    floats
  }

  /** Interleaved floats in -1 to 1, as PCM bytes in `format`. */
  def fromFloats(floats: Float32Array, format: AudioFormat): Array[Byte] = {
    val width = format.bitDepth / 8
    val bytes = new Array[Byte](floats.length * width)
    var i     = 0
    while (i < floats.length) {
      val sample = math.max(-1.0, math.min(1.0, floats(i).toDouble))
      val at     = i * width
      format.bitDepth match {
        case 8 =>
          bytes(at) = (math.round(sample * 127) + 128).toByte
        case 16 =>
          val v = math.round(sample * 32767).toInt
          bytes(at) = v.toByte
          bytes(at + 1) = (v >> 8).toByte
        case 24 =>
          val v = math.round(sample * 8388607).toInt
          bytes(at) = v.toByte
          bytes(at + 1) = (v >> 8).toByte
          bytes(at + 2) = (v >> 16).toByte
        case _ =>
          val v = math.round(sample * 2147483647).toInt
          bytes(at) = v.toByte
          bytes(at + 1) = (v >> 8).toByte
          bytes(at + 2) = (v >> 16).toByte
          bytes(at + 3) = (v >> 24).toByte
      }
      i += 1
    }
    bytes
  }
}
