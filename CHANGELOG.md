# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.4.0] - 2026-11-18

### Changed

- **Breaking:** the public API no longer exposes `javax.sound.sampled` types,
  so it can be implemented on other platforms.
  - `sound4s.AudioFormat(sampleRate, bitDepth, channels, signed, bigEndian)`
    replaces `javax.sound.sampled.AudioFormat`.
  - `sound4s.AudioDevice` replaces `Mixer.Info`. `MixerLookup` is replaced by
    `AudioDevices`, with `findByName` and a new `list`. The `mixerInfo`
    parameters are now `device: Option[AudioDevice]`.
  - `Microphone.resource` returns a `Microphone[F]` (with `source`) and
    `Speakers.resource` a `Speakers[F]` (with `sink`), instead of a raw
    `TargetDataLine` / `SourceDataLine`.
  - `AudioFile` takes a `sound4s.AudioFormat`.
  - Entry points require `Async[F]` instead of `Sync[F]`.

### Added

- The `sound4s-otel4s` module (JVM, Scala.js and Scala Native): `AudioTelemetry`
  reports metrics and traces on otel4s for `Microphone`, `Speakers` and
  `SoundStreaming`. The core library does not depend on it.
- Scala Native support for `AudioDevices`, `Microphone`, `Speakers` and
  `SoundStreaming`, on [miniaudio](https://miniaud.io) (vendored, public
  domain).
- Scala.js support for the same, in browsers, on Web Audio (AudioWorklet).
  Microphone access needs the user's permission, and audio may only start from
  a user gesture.
- `AudioFile` and `ResourceName` remain JVM-only.
- `AudioDevice.kinds` says whether a device captures, plays, or both.
- `AudioError.UnsupportedFormat` and `AudioError.DeviceFailure`.
- `AudioError.DeviceNotFound`, raised when a requested device is no longer
  installed.
- `AudioFormat.frameSize` and `AudioFormat.chunkSize`.

### Internal

- The build is a cross project (JVM, Scala.js and Scala Native); the sources are split into
  `core/shared` and `core/jvm`.

## [0.3.0] - 2026-11-03

### Changed

- Built with Scala 3.9.0 (was 3.3.8). **Breaking:** artifacts are no longer
  usable from Scala 3.3 LTS projects.
- Updated Cats Effect to 3.7.1 and fs2 to 3.14.0.

## [0.2.1] - 2026-11-03

### Fixed

- `SoundStreaming.sink` flushes the speaker line when it is cancelled or fails,
  and still plays to the end when it ends. Audio written as fast as the line takes
  it queues there (up to 500 ms for 8 kHz, 16-bit mono on macOS); a cancelled sink
  used to leave that queued, and what was written next played behind it, late.
- `SoundStreaming.source` flushes the microphone line when it is read. The line
  records from the moment it is opened into a buffer of its own (500 ms for 8 kHz,
  16-bit mono on macOS), so a source read some time after opening began with old
  audio.

## [0.2.0] - 2026-08-13

### Changed

- Renamed the project (and its `io.github.max-peroch` artifact) from
  `sounds4s` to `sound4s`, including the root package. **Breaking:** update
  imports from `sounds4s._` to `sound4s._` and the library dependency name.
- Enabled `-Wunused:all` and `-Xfatal-warnings` compiler options.

## [0.1.0] - 2026-08-12

### Added

- `MixerLookup` — open the system default or a named audio mixer as a
  `Resource`.
- `Microphone` / `Speakers` — open a `TargetDataLine` / `SourceDataLine` as a
  `Resource`.
- `SoundStreaming` — microphone and speakers opened together, exposing
  `source: Stream[F, Byte]` and `sink: Pipe[F, Byte, Unit]`.
- `AudioFile` — stream PCM from a classpath WAV / AU / AIFF resource,
  transcoded on-the-fly.
- `AudioError` — typed errors for unexpected line types and missing
  resources.
- Logging via `org.typelevel.log4cats.Logger[F]`.

[Unreleased]: https://github.com/max-peroch/sound4s/compare/v0.4.0...HEAD
[0.4.0]: https://github.com/max-peroch/sound4s/releases/tag/v0.4.0
[0.3.0]: https://github.com/max-peroch/sound4s/releases/tag/v0.3.0
[0.2.1]: https://github.com/max-peroch/sound4s/releases/tag/v0.2.1
[0.2.0]: https://github.com/max-peroch/sound4s/releases/tag/v0.2.0
[0.1.0]: https://github.com/max-peroch/sound4s/releases/tag/v0.1.0
