package sound4s

class ResourceNameSuite extends munit.FunSuite {

  test("accepts a resource that exists on the classpath") {
    val result = ResourceName("test-resource.txt")
    assert(result.isRight, s"Expected Right, got $result")
    assertEquals(result.map(_.value), Right("test-resource.txt"))
  }

  test("rejects a resource that does not exist") {
    val result = ResourceName("no-such-file.wav")
    assert(result.isLeft)
    assert(result.left.exists(_.contains("no-such-file.wav")))
  }
}
