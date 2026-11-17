package sound4s

import cats.effect.Async
import cats.effect.kernel.Resource
import org.typelevel.log4cats.Logger

import scala.concurrent.duration.FiniteDuration

private[sound4s] trait SoundStreamingCompanionPlatform {

  /** Opens the microphone and speakers together on the same device.
    *
    * @param audioFormat
    *   the PCM format for both capture and playback
    * @param logger
    *   receives an info message as each line is opened and closed
    * @param device
    *   the device to use, as returned by [[AudioDevices.findByName]]; `None`
    *   uses the system default
    * @param rtpPacketInterval
    *   the packet duration used to size `source`/`sink` chunks; defaults to 20
    *   ms
    * @return
    *   fails with [[AudioError.DeviceNotFound]] if `device` is no longer
    *   installed, or [[AudioError.UnexpectedLineType]] if the device doesn't
    *   expose the expected line types for this format
    */
  def resource[F[_]: Async](
      audioFormat: AudioFormat,
      logger: Logger[F],
      device: Option[AudioDevice] = None,
      rtpPacketInterval: FiniteDuration = AudioFormat.DefaultPacketInterval
  ): Resource[F, SoundStreaming[F]] =
    for {
      mixer <- JavaSoundBackend.mixer(device, logger)
      sdl   <- JavaSoundBackend.openSourceDataLine(mixer, audioFormat, logger)
      tdl   <- JavaSoundBackend.openTargetDataLine(mixer, audioFormat, logger)
    } yield new SoundStreaming(
      new Microphone(JavaSoundBackend.capture[F](tdl, audioFormat)),
      new Speakers(JavaSoundBackend.playback[F](sdl, audioFormat)),
      rtpPacketInterval
    )
}
