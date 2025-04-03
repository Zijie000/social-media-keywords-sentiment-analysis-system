// build.sbt
name := "MyAkkaTypedProject"

version := "0.1.0-SNAPSHOT"

// Scala 3.6.3
scalaVersion := "3.6.3"


ThisBuild / organization := "com.example"


val akkaVersion = "2.8.4"


libraryDependencies ++= Seq(
  //Akka Typed
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,

  //Typed Actors
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,

  // 
  // "com.typesafe.akka" %% "akka-serialization-jackson" % akkaVersion,

  "ch.qos.logback" % "logback-classic" % "1.4.11" % Runtime
)
