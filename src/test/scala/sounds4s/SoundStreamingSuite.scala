package sounds4s

import cats.effect.IO
import munit.CatsEffectSuite

import javax.sound.sampled.*
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.*

class SoundStreamingSuite extends CatsEffectSuite {

  private val format16BitMono: AudioFormat =
    new AudioFormat(8000f, 16, 1, true, false)

  private val format16BitStereo: AudioFormat =
    new AudioFormat(8000f, 16, 2, true, false)

  private def stubSourceDataLine(
      fmt: AudioFormat,
      writes: ArrayBuffer[Array[Byte]]
  ): SourceDataLine = new SourceDataLine {
    def write(b: Array[Byte], off: Int, len: Int): Int = {
      writes += java.util.Arrays.copyOfRange(b, off, off + len)
      len
    }
    def getFormat: AudioFormat                       = fmt
    def open(f: AudioFormat, bufSize: Int): Unit     = ()
    def open(f: AudioFormat): Unit                   = ()
    def drain(): Unit                                = ()
    def flush(): Unit                                = ()
    def start(): Unit                                = ()
    def stop(): Unit                                 = ()
    def isRunning: Boolean                           = false
    def isActive: Boolean                            = false
    def getBufferSize: Int                           = 0
    def available(): Int                             = 0
    def getFramePosition: Int                        = 0
    def getLongFramePosition: Long                   = 0L
    def getMicrosecondPosition: Long                 = 0L
    def getLevel: Float                              = 0f
    def getLineInfo: Line.Info                       = null
    def open(): Unit                                 = ()
    def close(): Unit                                = ()
    def isOpen: Boolean                              = false
    def getControls: Array[Control]                  = Array.empty
    def isControlSupported(t: Control.Type): Boolean = false
    def getControl(t: Control.Type): Control         = null
    def addLineListener(l: LineListener): Unit       = ()
    def removeLineListener(l: LineListener): Unit    = ()
  }

  private def stubTargetDataLine(fmt: AudioFormat): TargetDataLine =
    new TargetDataLine {
      def read(b: Array[Byte], off: Int, len: Int): Int = len
      def getFormat: AudioFormat                        = fmt
      def open(f: AudioFormat, bufSize: Int): Unit      = ()
      def open(f: AudioFormat): Unit                    = ()
      def drain(): Unit                                 = ()
      def flush(): Unit                                 = ()
      def start(): Unit                                 = ()
      def stop(): Unit                                  = ()
      def isRunning: Boolean                            = false
      def isActive: Boolean                             = false
      def getBufferSize: Int                            = 0
      def available(): Int                              = 0
      def getFramePosition: Int                         = 0
      def getLongFramePosition: Long                    = 0L
      def getMicrosecondPosition: Long                  = 0L
      def getLevel: Float                               = 0f
      def getLineInfo: Line.Info                        = null
      def open(): Unit                                  = ()
      def close(): Unit                                 = ()
      def isOpen: Boolean                               = false
      def getControls: Array[Control]                   = Array.empty
      def isControlSupported(t: Control.Type): Boolean  = false
      def getControl(t: Control.Type): Control          = null
      def addLineListener(l: LineListener): Unit        = ()
      def removeLineListener(l: LineListener): Unit     = ()
    }

  private def makeSink(
      sdl: SourceDataLine,
      tdl: TargetDataLine
  ): fs2.Pipe[IO, Byte, Unit] =
    new SoundStreaming[IO](sdl, tdl, 20.millis).sink

  test("sink writes frame-aligned chunks") {
    val writes = ArrayBuffer.empty[Array[Byte]]
    val sdl    = stubSourceDataLine(format16BitMono, writes)
    val tdl    = stubTargetDataLine(format16BitMono)
    val sink   = makeSink(sdl, tdl)
    val input  = Array.fill[Byte](7)(1)
    fs2.Stream.emits(input).through(sink).compile.drain.map { _ =>
      val totalWritten = writes.map(_.length).sum
      assertEquals(totalWritten % format16BitMono.getFrameSize, 0)
    }
  }

  test("sink discards sub-frame remnants for stereo") {
    val writes = ArrayBuffer.empty[Array[Byte]]
    val sdl    = stubSourceDataLine(format16BitStereo, writes)
    val tdl    = stubTargetDataLine(format16BitStereo)
    val sink   = makeSink(sdl, tdl)
    val input  = Array.fill[Byte](5)(1)
    fs2.Stream.emits(input).through(sink).compile.drain.map { _ =>
      assertEquals(writes.map(_.length).sum, 4)
    }
  }

  test("sink handles empty stream") {
    val writes = ArrayBuffer.empty[Array[Byte]]
    val sdl    = stubSourceDataLine(format16BitMono, writes)
    val tdl    = stubTargetDataLine(format16BitMono)
    val sink   = makeSink(sdl, tdl)
    fs2.Stream.empty.through(sink).compile.drain.map { _ =>
      assert(writes.isEmpty)
    }
  }

  test("sink preserves byte content") {
    val writes = ArrayBuffer.empty[Array[Byte]]
    val sdl    = stubSourceDataLine(format16BitMono, writes)
    val tdl    = stubTargetDataLine(format16BitMono)
    val sink   = makeSink(sdl, tdl)
    val input  = Array[Byte](0x01, 0x02, 0x03, 0x04)
    fs2.Stream.emits(input).through(sink).compile.drain.map { _ =>
      assertEquals(writes.flatMap(_.toVector).toVector, input.toVector)
    }
  }
}
