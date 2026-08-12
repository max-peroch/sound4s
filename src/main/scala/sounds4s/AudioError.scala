package sounds4s

/** Errors raised by sounds4s' audio hardware and file access. */
enum AudioError(msg: String) extends RuntimeException(msg) {

  /** A mixer line didn't match the type sounds4s expected for the requested
    * `AudioFormat`.
    */
  case UnexpectedLineType(expected: String, actual: String)
      extends AudioError(s"Expected $expected, got $actual")

  /** A classpath resource could not be read after having been validated to
    * exist by [[ResourceName.apply]].
    */
  case ResourceNotFound(path: String)
      extends AudioError(s"Audio resource not found: $path")
}
