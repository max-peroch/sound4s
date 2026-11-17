package sound4s

/** An audio device, as returned by [[AudioDevices.list]] and
  * [[AudioDevices.findByName]].
  *
  * @param id
  *   identifies the device to the platform backend; opaque to callers, and only
  *   meaningful on the platform and in the session that produced it
  * @param name
  *   a human-readable name
  */
final case class AudioDevice(id: String, name: String)
