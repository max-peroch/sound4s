package sound4s

/** An audio device, as returned by [[AudioDevices.list]] and
  * [[AudioDevices.findByName]].
  *
  * @param id
  *   identifies the device to the platform backend; opaque to callers, and only
  *   meaningful on the platform and in the session that produced it
  * @param name
  *   a human-readable name
  * @param kinds
  *   what the device can do: capture ([[AudioDevice.Kind.Input]]), play
  *   ([[AudioDevice.Kind.Output]]), or both
  */
final case class AudioDevice(
    id: String,
    name: String,
    kinds: Set[AudioDevice.Kind]
)

object AudioDevice {

  /** The direction of an audio device. */
  enum Kind {

    /** Captures audio, e.g. a microphone. */
    case Input

    /** Plays audio, e.g. speakers or headphones. */
    case Output
  }
}
