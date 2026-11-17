package sound4s

import cats.effect.IO
import munit.CatsEffectSuite
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.noop.NoOpLogger

import javax.sound.sampled.*

class SpeakersSuite extends CatsEffectSuite {

  private val logger: Logger[IO]       = NoOpLogger.impl[IO]
  private val audioFormat: AudioFormat =
    new AudioFormat(8000f, 16, 1, true, false)

  private class StubLine extends Line {
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

  private class StubMixer(line: Line) extends Mixer {
    def getLine(info: Line.Info): Line                               = line
    def getMixerInfo: Mixer.Info                                     = ???
    def getSourceLineInfo(): Array[Line.Info]                        = ???
    def getTargetLineInfo(): Array[Line.Info]                        = ???
    def getSourceLineInfo(info: Line.Info): Array[Line.Info]         = ???
    def getTargetLineInfo(info: Line.Info): Array[Line.Info]         = ???
    def isLineSupported(info: Line.Info): Boolean                    = ???
    def getMaxLines(info: Line.Info): Int                            = ???
    def getSourceLines(): Array[Line]                                = ???
    def getTargetLines(): Array[Line]                                = ???
    def synchronize(lines: Array[Line], maintainSync: Boolean): Unit = ???
    def unsynchronize(lines: Array[Line]): Unit                      = ???
    def isSynchronizationSupported(
        lines: Array[Line],
        maintainSync: Boolean
    ): Boolean                                       = ???
    def getLineInfo: Line.Info                       = ???
    def open(): Unit                                 = ???
    def close(): Unit                                = ???
    def isOpen: Boolean                              = ???
    def getControls: Array[Control]                  = ???
    def isControlSupported(t: Control.Type): Boolean = ???
    def getControl(t: Control.Type): Control         = ???
    def addLineListener(l: LineListener): Unit       = ???
    def removeLineListener(l: LineListener): Unit    = ???
  }

  test(
    "openSourceDataLine fails with UnexpectedLineType when the mixer's line isn't a SourceDataLine"
  ) {
    val mixer = new StubMixer(new StubLine)
    Speakers
      .openSourceDataLine[IO](mixer, audioFormat, logger)
      .use_
      .attempt
      .map {
        case Left(AudioError.UnexpectedLineType(expected, actual)) =>
          assertEquals(expected, "SourceDataLine")
          assertEquals(actual, "StubLine")
        case other => fail(s"expected UnexpectedLineType, got $other")
      }
  }
}
