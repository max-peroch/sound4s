package sound4s

import cats.effect.Async

private[sound4s] trait AudioDevicesCompanionPlatform {

  /** Lists the audio devices the browser reports.
    *
    * Browsers give no device names until the page has been granted microphone
    * permission, and list the input and output sides of a device separately.
    *
    * @return
    *   fails with [[AudioError.DeviceFailure]] outside a browser
    */
  def list[F[_]: Async]: F[List[AudioDevice]] = WebAudioBackend.devices[F]
}
