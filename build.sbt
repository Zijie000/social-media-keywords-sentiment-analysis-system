name := "MyProject"

version := "0.1.0-SNAPSHOT"

scalaVersion := "3.3.1"

ThisBuild / organization := "com.example"

val akkaVersion = "2.8.4"

libraryDependencies ++= Seq(
  // Akka Typed Core
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,

  // Logging
  "ch.qos.logback" % "logback-classic" % "1.4.11" % Runtime,

  // HTTP Client for youtube API
  "com.softwaremill.sttp.client3" %% "core" % "3.8.15",

  // JSON parsing
  "org.json4s" %% "json4s-native" % "4.0.6"
)
