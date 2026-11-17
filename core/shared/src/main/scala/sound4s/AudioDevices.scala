package sound4s

import cats.effect.Async
import cats.syntax.all.*

object AudioDevices extends AudioDevicesCompanionPlatform {

  /** Looks up an installed audio device whose name contains `name`.
    *
    * @param name
    *   a substring to match against each device's name, e.g. a device name
    *   fragment reported by the OS
    * @return
    *   the first matching device, or `None` if no device matches
    */
  def findByName[F[_]: Async](name: String): F[Option[AudioDevice]] =
    list[F].map(_.find(_.name.contains(name)))
}
