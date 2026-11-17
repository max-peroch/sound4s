package sound4s

import cats.effect.kernel.{Async, Resource, Sync}
import cats.effect.std.{Dispatcher, Queue}
import cats.effect.syntax.all.*
import cats.syntax.all.*
import org.typelevel.log4cats.Logger

import scala.concurrent.TimeoutException
import scala.concurrent.duration.*
import scala.scalajs.js
import scala.scalajs.js.typedarray.Float32Array

/** The Web Audio implementation of the audio backend, for browsers.
  *
  * Capture and playback run in AudioWorklets, loaded from a `Blob`, which
  * exchange PCM with the main thread by message passing.
  */
private[sound4s] object WebAudioBackend {

  /** How long the audio held between the worklet and Scala may be. */
  private val BufferMillis = 500

  /** The longest to wait for the browser to start an audio context. */
  private val ResumeTimeout = 2.seconds

  /** The Web Audio render quantum, in frames. */
  private val QuantumFrames = 128

  private def message(error: Any): String = {
    val dyn = error.asInstanceOf[js.Dynamic]
    val msg = js.typeOf(dyn.message) match {
      case "string" => dyn.message.asInstanceOf[String]
      case _        => String.valueOf(error)
    }
    val name = js.typeOf(dyn.name) match {
      case "string" => dyn.name.asInstanceOf[String]
      case _        => ""
    }
    if (name.isEmpty) msg else s"$name: $msg"
  }

  /** Turns an error the browser raised into an [[AudioError]]. */
  private def translate(device: String, error: Throwable): Throwable =
    error match {
      case js.JavaScriptException(e) =>
        val name = js.typeOf(e.asInstanceOf[js.Dynamic].name) match {
          case "string" => e.asInstanceOf[js.Dynamic].name.asInstanceOf[String]
          case _        => ""
        }
        name match {
          case "NotFoundError" | "OverconstrainedError" =>
            AudioError.DeviceNotFound(device)
          case "NotAllowedError" | "SecurityError" =>
            AudioError.DeviceFailure(
              device,
              s"permission denied (${message(e)})"
            )
          case _ => AudioError.DeviceFailure(device, message(e))
        }
      case other => other
    }

  private def translated[F[_]: Sync, A](device: String)(fa: F[A]): F[A] =
    fa.adaptError { case e: js.JavaScriptException => translate(device, e) }

  private def promise[F[_]: Async, A](thunk: => js.Dynamic): F[A] =
    Async[F].fromPromise(Sync[F].delay(thunk.asInstanceOf[js.Promise[A]]))

  private def unavailable(what: String): Throwable =
    AudioError.DeviceFailure(
      "web audio",
      s"$what is not available: this needs a browser"
    )

  private def mediaDevices[F[_]: Sync]: F[js.Dynamic] =
    Sync[F].defer {
      if (
        js.typeOf(js.Dynamic.global.navigator) != "undefined" &&
        !js.isUndefined(js.Dynamic.global.navigator.mediaDevices)
      ) Sync[F].pure(js.Dynamic.global.navigator.mediaDevices)
      else Sync[F].raiseError(unavailable("navigator.mediaDevices"))
    }

  private def deviceKind(kind: String): Option[AudioDevice.Kind] =
    kind match {
      case "audioinput"  => Some(AudioDevice.Kind.Input)
      case "audiooutput" => Some(AudioDevice.Kind.Output)
      case _             => None
    }

  /** The audio devices the browser reports. Browsers give no names until the
    * page has been granted microphone permission, and list each direction of a
    * device on its own.
    */
  def devices[F[_]: Async]: F[List[AudioDevice]] =
    mediaDevices[F]
      .flatMap { md =>
        translated("devices")(
          promise[F, js.Array[js.Dynamic]](md.enumerateDevices())
        )
      }
      .map(_.toList.flatMap { d =>
        val id    = d.deviceId.asInstanceOf[String]
        val label = d.label.asInstanceOf[String]
        deviceKind(d.kind.asInstanceOf[String]).map { kind =>
          val name =
            if (label.nonEmpty) label
            else s"${kind.toString} ${id.take(8)}"
          AudioDevice(id, name, Set(kind))
        }
      })

  private def requireSupported[F[_]: Sync](format: AudioFormat): F[Unit] =
    Sync[F].raiseUnless(PcmConversion.supports(format))(
      AudioError.UnsupportedFormat(
        format,
        "only little-endian 8-bit unsigned and 16, 24 or 32-bit signed PCM"
      )
    )

  private def newContext[F[_]: Sync](format: AudioFormat): F[js.Dynamic] =
    Sync[F].defer {
      if (js.typeOf(js.Dynamic.global.AudioContext) == "undefined")
        Sync[F].raiseError(unavailable("AudioContext"))
      else
        Sync[F]
          .delay(
            js.Dynamic.newInstance(js.Dynamic.global.AudioContext)(
              js.Dynamic.literal(
                sampleRate = format.sampleRate,
                latencyHint = "interactive"
              )
            )
          )
          .adaptError { case js.JavaScriptException(e) =>
            AudioError.UnsupportedFormat(
              format,
              s"the browser can't run audio at this sample rate (${message(e)})"
            )
          }
    }

  /** An audio context at the format's sample rate, running and with the
    * worklets loaded, closed on release.
    */
  private def audioContext[F[_]: Async](
      format: AudioFormat,
      deviceId: String,
      outputDevice: Option[AudioDevice]
  ): Resource[F, js.Dynamic] =
    Resource
      .make(newContext[F](format))(ctx =>
        promise[F, Unit](ctx.close()).void.handleError(_ => ())
      )
      .evalTap { ctx =>
        translated(deviceId) {
          for {
            _ <- outputDevice.traverse_ { device =>
              if (js.typeOf(ctx.setSinkId) == "function")
                promise[F, Unit](ctx.setSinkId(device.id))
              else
                Sync[F].raiseError[Unit](
                  AudioError.DeviceFailure(
                    device.id,
                    "this browser can't choose the output device"
                  )
                )
            }
            _ <- loadWorklets(ctx)
            _ <- resume(ctx, deviceId)
          } yield ()
        }
      }

  private def loadWorklets[F[_]: Async](ctx: js.Dynamic): F[Unit] =
    Resource
      .make(
        Sync[F].delay {
          val blob = js.Dynamic.newInstance(js.Dynamic.global.Blob)(
            js.Array(Worklets.Source),
            js.Dynamic.literal("type" -> "application/javascript")
          )
          js.Dynamic.global.URL.createObjectURL(blob).asInstanceOf[String]
        }
      )(url => Sync[F].delay(js.Dynamic.global.URL.revokeObjectURL(url)))
      .use(url => promise[F, Unit](ctx.audioWorklet.addModule(url)))

  private def resume[F[_]: Async](ctx: js.Dynamic, deviceId: String): F[Unit] =
    promise[F, Unit](ctx.resume())
      .timeoutTo(
        ResumeTimeout,
        Async[F].raiseError(new TimeoutException("resume timed out"))
      )
      .adaptError { case _: TimeoutException => notStarted(deviceId) }
      .flatMap(_ =>
        Sync[F].raiseWhen(ctx.state.asInstanceOf[String] != "running")(
          notStarted(deviceId)
        )
      )

  private def notStarted(deviceId: String): Throwable =
    AudioError.DeviceFailure(
      deviceId,
      "the browser did not start audio; open it from a user gesture such as a click"
    )

  private def workletNode(
      ctx: js.Dynamic,
      name: String,
      options: js.Dynamic
  ): js.Dynamic =
    js.Dynamic
      .newInstance(js.Dynamic.global.AudioWorkletNode)(ctx, name, options)

  def openCapture[F[_]: Async](
      format: AudioFormat,
      device: Option[AudioDevice],
      logger: Logger[F]
  ): Resource[F, AudioCapture[F]] = {
    val deviceId = device.fold("default")(_.id)
    val blocks   =
      (format.sampleRate.toLong * BufferMillis / 1000 / QuantumFrames).toInt + 1
    for {
      _      <- Resource.onFinalize(logger.info("Microphone closed"))
      _      <- Resource.eval(requireSupported(format))
      stream <- Resource.make(
        mediaDevices[F].flatMap(md =>
          translated(deviceId)(
            promise[F, js.Dynamic](md.getUserMedia(constraints(format, device)))
          )
        )
      )(stream =>
        Sync[F].delay(
          stream
            .getTracks()
            .asInstanceOf[js.Array[js.Dynamic]]
            .foreach(_.stop())
        )
      )
      ctx        <- audioContext[F](format, deviceId, None)
      dispatcher <- Dispatcher.sequential[F]
      queue      <- Resource.eval(Queue.circularBuffer[F, Array[Byte]](blocks))
      _          <- Resource.make(
        Sync[F].delay {
          val node = workletNode(
            ctx,
            Worklets.CaptureName,
            js.Dynamic.literal(
              numberOfInputs = 1,
              numberOfOutputs = 1,
              channelCount = format.channels,
              channelCountMode = "explicit",
              outputChannelCount = js.Array(format.channels)
            )
          )
          node.port.updateDynamic("onmessage")({ (event: js.Dynamic) =>
            val floats = event.data.asInstanceOf[Float32Array]
            dispatcher.unsafeRunAndForget(
              queue.offer(PcmConversion.fromFloats(floats, format))
            )
          }: js.Function1[js.Dynamic, Unit])
          val source = ctx.createMediaStreamSource(stream)
          source.connect(node)
          node.connect(ctx.destination)
          (source, node)
        }
      ) { case (source, node) =>
        Sync[F].delay {
          source.disconnect()
          node.disconnect()
        }
      }
      _ <- Resource.eval(logger.info("Microphone opened"))
    } yield new WebCapture[F](queue, format)
  }

  private def constraints(
      format: AudioFormat,
      device: Option[AudioDevice]
  ): js.Dynamic = {
    val audio = js.Dynamic.literal(
      channelCount = js.Dynamic.literal(ideal = format.channels),
      sampleRate = js.Dynamic.literal(ideal = format.sampleRate),
      echoCancellation = false,
      noiseSuppression = false,
      autoGainControl = false
    )
    device.foreach(d =>
      audio.updateDynamic("deviceId")(js.Dynamic.literal(exact = d.id))
    )
    js.Dynamic.literal(audio = audio)
  }

  def openPlayback[F[_]: Async](
      format: AudioFormat,
      device: Option[AudioDevice],
      logger: Logger[F]
  ): Resource[F, AudioPlayback[F]] = {
    val deviceId = device.fold("default")(_.id)
    for {
      _        <- Resource.onFinalize(logger.info("Speakers closed"))
      _        <- Resource.eval(requireSupported(format))
      ctx      <- audioContext[F](format, deviceId, device)
      playback <- Resource.make(
        Sync[F].delay {
          val node = workletNode(
            ctx,
            Worklets.PlaybackName,
            js.Dynamic.literal(
              numberOfInputs = 0,
              numberOfOutputs = 1,
              outputChannelCount = js.Array(format.channels),
              processorOptions = js.Dynamic.literal(channels = format.channels)
            )
          )
          val playback = new WebPlayback[F](
            node.port,
            format,
            (format.sampleRate.toLong * BufferMillis / 1000).toInt
          )
          node.port.updateDynamic("onmessage")({ (event: js.Dynamic) =>
            playback.reportConsumed(event.data.consumed.asInstanceOf[Double])
          }: js.Function1[js.Dynamic, Unit])
          node.connect(ctx.destination)
          (node, playback)
        }
      ) { case (node, playback) =>
        playback.drain((BufferMillis + 200).millis) >>
          Sync[F].delay(node.disconnect())
      }
      _ <- Resource.eval(logger.info("Speakers opened"))
    } yield playback._2
  }
}
