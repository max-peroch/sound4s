package sound4s

import cats.effect.Async
import cats.effect.kernel.Resource
import org.typelevel.log4cats.Logger

import scala.concurrent.duration.FiniteDuration

private[sound4s] trait SoundStreamingCompanionPlatform {

  /** Opens the microphone and speakers together.
    *
    * Browsers list each direction of a device on its own, so `device` is used
    * for the direction it has, and the browser's default for the other.
    *
    * @param audioFormat
    *   the PCM format for both capture and playback; see
    *   [[Microphone.resource]]
    * @param logger
    *   receives an info message as each is opened and closed
    * @param device
    *   the device to use, as returned by [[AudioDevices.list]]; `None` uses the
    *   browser's defaults
    * @param rtpPacketInterval
    *   the packet duration used to size `source`/`sink` chunks; defaults to 20
    *   ms
    * @return
    *   fails like [[Microphone.resource]] and [[Speakers.resource]]
    */
  def resource[F[_]: Async](
      audioFormat: AudioFormat,
      logger: Logger[F],
      device: Option[AudioDevice] = None,
      rtpPacketInterval: FiniteDuration = AudioFormat.DefaultPacketInterval
  ): Resource[F, SoundStreaming[F]] =
    for {
      speakers <- Speakers.resource[F](
        audioFormat,
        logger,
        device.filter(_.kinds.contains(AudioDevice.Kind.Output))
      )
      microphone <- Microphone.resource[F](
        audioFormat,
        logger,
        device.filter(_.kinds.contains(AudioDevice.Kind.Input))
      )
    } yield new SoundStreaming(microphone, speakers, rtpPacketInterval)
}
