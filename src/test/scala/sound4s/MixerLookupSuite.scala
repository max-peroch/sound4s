package sound4s

import cats.effect.IO
import munit.CatsEffectSuite
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.noop.NoOpLogger

import javax.sound.sampled.AudioSystem

class MixerLookupSuite extends CatsEffectSuite {

  private val logger: Logger[IO] = NoOpLogger.impl[IO]

  test("findByName returns None for a nonexistent device") {
    MixerLookup
      .findByName[IO]("__no_such_device_42__")
      .map(info => assertEquals(info, None))
  }

  test("resource acquires a usable default mixer") {
    assume(
      AudioSystem.getMixerInfo.nonEmpty,
      "no audio mixer available in this environment"
    )
    MixerLookup
      .resource[IO](None, logger)
      .use(mixer => IO(assert(mixer.getMixerInfo != null)))
  }
}
