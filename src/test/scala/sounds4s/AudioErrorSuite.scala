package sounds4s

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
}
