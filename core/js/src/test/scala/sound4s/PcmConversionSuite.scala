package sound4s

import scala.scalajs.js.typedarray.Float32Array

class PcmConversionSuite extends munit.FunSuite {

  private val s16 = AudioFormat(8000, 16, 1)

  test("16-bit samples map to floats in -1 to 1") {
    val bytes  = Array[Byte](0, 0, -1, 127, 0, -128)
    val floats = PcmConversion.toFloats(bytes, bytes.length, s16)
    assertEquals(floats.length, 3)
    assertEquals(floats(0).toDouble, 0.0)
    assertEqualsDouble(floats(1).toDouble, 32767.0 / 32768.0, 1e-6)
    assertEquals(floats(2).toDouble, -1.0)
  }

  test("8-bit unsigned samples are centred on 128") {
    val format = AudioFormat(8000, 8, 1, signed = false)
    val bytes  = Array[Byte](128.toByte, 0, 255.toByte)
    val floats = PcmConversion.toFloats(bytes, bytes.length, format)
    assertEquals(floats(0).toDouble, 0.0)
    assertEquals(floats(1).toDouble, -1.0)
    assertEqualsDouble(floats(2).toDouble, 127.0 / 128.0, 1e-6)
  }

  test("only the first `length` bytes are converted") {
    val bytes = Array[Byte](0, 0, 1, 1, 2, 2)
    assertEquals(PcmConversion.toFloats(bytes, 4, s16).length, 2)
  }

  test("floats beyond -1 to 1 are clamped") {
    val floats = new Float32Array(scala.scalajs.js.Array(2.0f, -2.0f))
    val bytes  = PcmConversion.fromFloats(floats, s16)
    assertEquals(bytes.toList, List[Byte](-1, 127, 1, -128))
  }

  test("every supported format round-trips through floats") {
    val formats = List(
      AudioFormat(8000, 8, 1, signed = false),
      AudioFormat(8000, 16, 2),
      AudioFormat(8000, 24, 1),
      AudioFormat(8000, 32, 1)
    )
    formats.foreach { format =>
      val width  = format.bitDepth / 8
      val bytes  = Array.tabulate[Byte](width * 64)(i => (i * 37 + 11).toByte)
      val floats = PcmConversion.toFloats(bytes, bytes.length, format)
      val back   = PcmConversion.fromFloats(floats, format)
      assertEquals(back.length, bytes.length, s"length for $format")
      val again = PcmConversion.toFloats(back, back.length, format)
      (0 until floats.length).foreach { i =>
        assertEqualsDouble(
          again(i).toDouble,
          floats(i).toDouble,
          2.0 / (1L << (format.bitDepth - 1)).toDouble,
          s"sample $i of $format"
        )
      }
    }
  }

  test("supports little-endian 8-bit unsigned and 16, 24, 32-bit signed") {
    assert(PcmConversion.supports(s16))
    assert(PcmConversion.supports(AudioFormat(8000, 8, 1, signed = false)))
    assert(PcmConversion.supports(AudioFormat(8000, 24, 1)))
    assert(!PcmConversion.supports(AudioFormat(8000, 8, 1)))
    assert(!PcmConversion.supports(AudioFormat(8000, 12, 1)))
    assert(!PcmConversion.supports(s16.copy(bigEndian = true)))
  }
}
