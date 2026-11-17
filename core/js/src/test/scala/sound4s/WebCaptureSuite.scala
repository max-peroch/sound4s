package sound4s

import cats.effect.IO
import cats.effect.std.Queue
import munit.CatsEffectSuite

class WebCaptureSuite extends CatsEffectSuite {

  private val mono = AudioFormat(8000, 16, 1)

  private def block(n: Int, value: Byte): Array[Byte] =
    Array.fill[Byte](n)(value)

  test("read regroups the posted blocks into chunks of the requested size") {
    for {
      queue  <- Queue.unbounded[IO, Array[Byte]]
      _      <- queue.offer(block(256, 1))
      _      <- queue.offer(block(256, 2))
      _      <- queue.offer(block(256, 3))
      chunks <- new WebCapture[IO](queue, mono)
        .read(320)
        .take(320 * 2)
        .chunks
        .compile
        .toList
    } yield {
      assertEquals(chunks.map(_.size).sum, 640)
      assert(chunks.forall(_.size == 320), chunks.map(_.size).toString)
      val bytes = chunks.flatMap(_.toList)
      assertEquals(bytes.take(256).distinct, List[Byte](1))
      assertEquals(bytes.slice(256, 512).distinct, List[Byte](2))
    }
  }

  test("flush discards the blocks queued before reading began") {
    for {
      queue <- Queue.unbounded[IO, Array[Byte]]
      capture = new WebCapture[IO](queue, mono)
      _     <- queue.offer(block(256, 1))
      _     <- capture.flush
      _     <- queue.offer(block(320, 9))
      chunk <- capture.read(320).take(320).chunks.compile.lastOrError
    } yield assertEquals(chunk.toList.distinct, List[Byte](9))
  }

  test("a circular queue drops the oldest blocks when the reader is behind") {
    for {
      queue  <- Queue.circularBuffer[IO, Array[Byte]](2)
      _      <- queue.offer(block(320, 1))
      _      <- queue.offer(block(320, 2))
      _      <- queue.offer(block(320, 3))
      chunks <- new WebCapture[IO](queue, mono)
        .read(320)
        .take(640)
        .chunks
        .compile
        .toList
    } yield assertEquals(
      chunks.flatMap(_.toList).distinct,
      List[Byte](2, 3)
    )
  }
}
