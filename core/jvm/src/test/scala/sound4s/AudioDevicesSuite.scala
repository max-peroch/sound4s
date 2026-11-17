package sound4s

import cats.effect.IO
import munit.CatsEffectSuite

import javax.sound.sampled.AudioSystem

class AudioDevicesSuite extends CatsEffectSuite {

  test("findByName returns None for a nonexistent device") {
    AudioDevices
      .findByName[IO]("__no_such_device_42__")
      .map(device => assertEquals(device, None))
  }

  test("list returns one device per installed mixer") {
    AudioDevices
      .list[IO]
      .map(devices =>
        assertEquals(devices.size, AudioSystem.getMixerInfo.length)
      )
  }

  test("findByName finds an installed device by part of its name") {
    assume(
      AudioSystem.getMixerInfo.nonEmpty,
      "no audio mixer available in this environment"
    )
    val name = AudioSystem.getMixerInfo.head.getName
    AudioDevices
      .findByName[IO](name)
      .map(device => assert(device.exists(_.name.contains(name))))
  }
}
