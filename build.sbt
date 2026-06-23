ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / name := "risc-core"
ThisBuild / organization := "com.github.ryarnyah"
ThisBuild / scalaVersion := "2.13.18"

val spinalVersion = "1.14.2"
val scalaTestVersion = "3.2.20"
val scalaTest = "org.scalatest" %% "scalatest" % scalaTestVersion % "test"
val spinalCore = "com.github.spinalhdl" %% "spinalhdl-core" % spinalVersion
val spinalLib = "com.github.spinalhdl" %% "spinalhdl-lib" % spinalVersion
val spinalIdslPlugin = compilerPlugin("com.github.spinalhdl" %% "spinalhdl-idsl-plugin" % spinalVersion)

lazy val root = (project in file("."))
  .settings(
    name := "risc-core",
    libraryDependencies ++= Seq(spinalCore, spinalLib, spinalIdslPlugin, scalaTest),
    Compile / mainClass := Some("risc.Soc"),
    fork := true,
    scalacOptions += "-deprecation"
  )
fork := true
