package sound4s

import cats.effect.kernel.Async
import cats.syntax.all.*

import scala.concurrent.duration.*
import scala.scalajs.js

/** Playback through the playback worklet behind `port`.
  *
  * The worklet takes audio as fast as it is posted, so writes are paced here: a
  * write waits while the worklet holds `capacityFrames` or more that it has not
  * yet played. The worklet reports how many frames it has taken in total
  * (played or dropped by a flush), through [[reportConsumed]].
  *
  * Everything runs on the browser's one thread, so the counters need no
  * guarding.
  */
private[sound4s] final class WebPlayback[F[_]: Async](
    port: js.Dynamic,
    audioFormat: AudioFormat,
    capacityFrames: Int
) extends AudioPlayback[F] {

  private val PollInterval = 5.millis

  private var sent: Double     = 0
  private var consumed: Double = 0

  def format: AudioFormat = audioFormat

  /** Records the worklet's running total of frames taken. */
  def reportConsumed(total: Double): Unit =
    consumed = math.max(consumed, total)

  /** The frames posted to the worklet and not yet taken by it. */
  def queuedFrames: F[Double] = Async[F].delay(sent - consumed)

  def write(bytes: Array[Byte], length: Int): F[Unit] = {
    val frames = length / audioFormat.frameSize
    waitForRoom(frames) >> Async[F].delay {
      val floats = PcmConversion.toFloats(bytes, length, audioFormat)
      port.postMessage(floats, js.Array(floats.buffer))
      sent += frames
    }
  }

  private def waitForRoom(frames: Int): F[Unit] =
    queuedFrames.flatMap { queued =>
      if (queued == 0 || queued + frames <= capacityFrames) Async[F].unit
      else Async[F].sleep(PollInterval) >> waitForRoom(frames)
    }

  def flush: F[Unit] = Async[F].delay {
    port.postMessage(js.Dynamic.literal(flush = true))
    consumed = sent
  }

  /** Waits for the worklet to play what it holds, for at most `timeout`. */
  def drain(timeout: FiniteDuration): F[Unit] = {
    def loop(waited: FiniteDuration): F[Unit] =
      queuedFrames.flatMap { queued =>
        if (queued <= 0 || waited >= timeout) Async[F].unit
        else Async[F].sleep(PollInterval) >> loop(waited + PollInterval)
      }
    loop(Duration.Zero)
  }
}
