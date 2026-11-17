package sound4s

import scala.concurrent.duration.*

class AudioFormatSuite extends munit.FunSuite {

  test("frameSize is the bytes of one sample for every channel") {
    assertEquals(AudioFormat(8000, 16, 1).frameSize, 2)
    assertEquals(AudioFormat(8000, 16, 2).frameSize, 4)
    assertEquals(AudioFormat(8000, 8, 1).frameSize, 1)
  }

  test("frameSize rounds a sample's bits up to whole bytes") {
    assertEquals(AudioFormat(8000, 12, 1).frameSize, 2)
    assertEquals(AudioFormat(8000, 24, 2).frameSize, 6)
  }

  test("chunkSize is a whole number of frames for the interval") {
    assertEquals(AudioFormat(8000, 16, 1).chunkSize(20.millis), 320)
    assertEquals(AudioFormat(8000, 16, 2).chunkSize(20.millis), 640)
    assertEquals(AudioFormat(16000, 16, 1).chunkSize(20.millis), 640)
  }

  test("defaults to signed little-endian") {
    val format = AudioFormat(8000, 16, 1)
    assert(format.signed)
    assert(!format.bigEndian)
  }

  test("rejects a non-positive sample rate, bit depth or channel count") {
    intercept[IllegalArgumentException](AudioFormat(0, 16, 1))
    intercept[IllegalArgumentException](AudioFormat(8000, 0, 1))
    intercept[IllegalArgumentException](AudioFormat(8000, 16, 0))
  }
}
