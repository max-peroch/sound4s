package sound4s

import cats.effect.Async
import cats.effect.kernel.Resource
import org.typelevel.log4cats.Logger

private[sound4s] trait MicrophoneCompanionPlatform {

  /** Opens the microphone, starting capture on acquisition and stopping it on
    * release.
    *
    * @param audioFormat
    *   the PCM format to capture in: 8-bit unsigned or 16, 24 or 32-bit signed,
    *   little-endian. The device's own rate is converted to `sampleRate`.
    * @param logger
    *   receives an info message when the microphone is opened and closed
    * @param device
    *   the device to capture from, as returned by [[AudioDevices.findByName]];
    *   `None` uses the system default
    * @return
    *   fails with [[AudioError.DeviceNotFound]] if `device` is no longer
    *   installed, [[AudioError.UnsupportedFormat]] if the format can't be
    *   captured, or [[AudioError.DeviceFailure]] if the device can't be opened
    */
  def resource[F[_]: Async](
      audioFormat: AudioFormat,
      logger: Logger[F],
      device: Option[AudioDevice] = None
  ): Resource[F, Microphone[F]] =
    NativeBackend
      .openCapture[F](audioFormat, device, logger)
      .map(new Microphone(_))
}
