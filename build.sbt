ThisBuild / scalaVersion := "3.9.0"
ThisBuild / organization := "io.github.max-peroch"
ThisBuild / homepage     := Some(uri("https://github.com/max-peroch/sound4s"))
ThisBuild / licenses     := List(License.Apache2)
ThisBuild / developers   := List(
  Developer(
    "max-peroch",
    "Maxime Perocheau",
    "max.peroch@hotmail.fr",
    uri("https://perocheau.com")
  )
)
ThisBuild / scalafmtOnCompile := true
ThisBuild / scalacOptions     := Seq(
  "-Wunused:all",
  "-Werror"
)

lazy val root = rootProject
  .aggregate(core.jvm)
  .settings(publish / skip := true)

lazy val core = crossProject(JVMPlatform)
  .crossType(CrossType.Full)
  .in(file("core"))
  .settings(
    name := "sound4s",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-effect"       % "3.7.1",
      "co.fs2"        %% "fs2-io"            % "3.14.0",
      "co.fs2"        %% "fs2-core"          % "3.14.0",
      "org.typelevel" %% "log4cats-core"     % "2.8.0",
      "org.scalameta" %% "munit"             % "1.3.6" % Test,
      "org.typelevel" %% "munit-cats-effect" % "2.2.1" % Test,
      "org.typelevel" %% "log4cats-noop"     % "2.8.0" % Test
    )
  )
