package sound4s

/** The name of a resource known to exist on the classpath, as validated by
  * [[ResourceName.apply]].
  */
opaque type ResourceName = String

object ResourceName {

  /** Validates that `name` resolves to a resource on the current thread's
    * classloader.
    *
    * @param name
    *   a classpath-relative resource path, e.g. `"audio/announcement.wav"`
    * @return
    *   `Right` wrapping `name` if it exists, or `Left` with a message if it
    *   doesn't
    */
  def apply(name: String): Either[String, ResourceName] =
    Option(Thread.currentThread().getContextClassLoader.getResource(name))
      .toRight(s"Resource not found on classpath: $name")
      .map(_ => name)

  extension (rn: ResourceName) def value: String = rn
}
