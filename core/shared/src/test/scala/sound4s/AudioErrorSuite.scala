package sound4s

class AudioErrorSuite extends munit.FunSuite {

  test("UnexpectedLineType formats expected and actual types") {
    val error = AudioError.UnexpectedLineType("SourceDataLine", "Port")
    assertEquals(error.getMessage, "Expected SourceDataLine, got Port")
  }

  test("ResourceNotFound formats the missing path") {
    val error = AudioError.ResourceNotFound("audio/missing.wav")
    assertEquals(
      error.getMessage,
      "Audio resource not found: audio/missing.wav"
    )
  }

  test("DeviceNotFound formats the missing device id") {
    val error = AudioError.DeviceNotFound("Built-in|Apple|1.0|Speakers")
    assertEquals(
      error.getMessage,
      "Audio device not found: Built-in|Apple|1.0|Speakers"
    )
  }

  test("UnsupportedFormat names the format and the reason") {
    val error = AudioError.UnsupportedFormat(
      AudioFormat(8000, 16, 1, bigEndian = true),
      "big-endian samples"
    )
    assert(error.getMessage.contains("big-endian samples"))
    assert(
      error.getMessage.contains("sampleRate=8000") || error.getMessage.contains(
        "AudioFormat(8000,16,1"
      )
    )
  }

  test("DeviceFailure names the device and the reason") {
    val error = AudioError.DeviceFailure("default", "could not be started")
    assertEquals(
      error.getMessage,
      "Audio device default failed: could not be started"
    )
  }
}
