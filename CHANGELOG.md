# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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

[Unreleased]: https://github.com/max-peroch/sound4s/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/max-peroch/sound4s/releases/tag/v0.2.0
[0.1.0]: https://github.com/max-peroch/sound4s/releases/tag/v0.1.0
