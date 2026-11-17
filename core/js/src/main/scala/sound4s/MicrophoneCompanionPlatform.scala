package sound4s

import cats.effect.Async
import cats.effect.kernel.Resource
import org.typelevel.log4cats.Logger

private[sound4s] trait MicrophoneCompanionPlatform {

  /** Opens the microphone through the browser, starting capture on acquisition
    * and stopping it on release.
    *
    * The browser asks the user for permission, and may require this to be
    * called from a user gesture such as a click. Audio is captured raw: echo
    * cancellation, noise suppression and automatic gain are off.
    *
    * @param audioFormat
    *   the PCM format to capture in: 8-bit unsigned or 16, 24 or 32-bit signed,
    *   little-endian. The browser must support audio at this sample rate (most
    *   do for 8 kHz and up; Firefox can't capture at a rate other than the
    *   device's).
    * @param logger
    *   receives an info message when the microphone is opened and closed
    * @param device
    *   the device to capture from, as returned by [[AudioDevices.list]]; `None`
    *   uses the browser's default
    * @return
    *   fails with [[AudioError.DeviceNotFound]] if `device` is gone or isn't a
    *   microphone, [[AudioError.UnsupportedFormat]] if the format or sample
    *   rate can't be used, or [[AudioError.DeviceFailure]] if permission is
    *   denied or Web Audio is not available (e.g. outside a browser)
    */
  def resource[F[_]: Async](
      audioFormat: AudioFormat,
      logger: Logger[F],
      device: Option[AudioDevice] = None
  ): Resource[F, Microphone[F]] =
    WebAudioBackend
      .openCapture[F](audioFormat, device, logger)
      .map(new Microphone(_))
}
