package sound4s

import cats.effect.Async

private[sound4s] trait AudioDevicesCompanionPlatform {

  /** Lists the installed audio devices. */
  def list[F[_]: Async]: F[List[AudioDevice]] = NativeBackend.devices[F]
}
