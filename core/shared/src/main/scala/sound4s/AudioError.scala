package sound4s

/** Errors raised by sound4s' audio hardware and file access. */
enum AudioError(msg: String) extends RuntimeException(msg) {

  /** A mixer line didn't match the type sound4s expected for the requested
    * [[AudioFormat]].
    */
  case UnexpectedLineType(expected: String, actual: String)
      extends AudioError(s"Expected $expected, got $actual")

  /** No audio device matches the [[AudioDevice]] that was asked for, e.g.
    * because it was unplugged since it was listed.
    */
  case DeviceNotFound(id: String)
      extends AudioError(s"Audio device not found: $id")

  /** A classpath resource could not be read after having been validated to
    * exist by [[ResourceName.apply]].
    */
  case ResourceNotFound(path: String)
      extends AudioError(s"Audio resource not found: $path")
}
