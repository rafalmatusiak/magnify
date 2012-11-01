organization := "magnify"

name := "magnify"

version := "0.1.0-SNAPSHOT"

scalaVersion := "2.9.2"

resolvers ++= Seq(
  "Typesafe releases" at "http://repo.typesafe.com/typesafe/releases/",
  "Spray repo" at "http://repo.spray.cc/",
  "OSS Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases/",
  "OSS Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/")

scalacOptions ++= Seq("-deprecation", "-unchecked", "-Ydependent-method-types")

libraryDependencies ++= Seq(
  "io.spray" % "spray-routing" % "1.0-M4.2",
  "io.spray" % "spray-can" % "1.0-M4.2",
  "io.spray" % "spray-client" % "1.0-M4.2",
  "io.spray" %% "spray-json" % "1.2.2",
  "io.spray" % "spray-testkit" % "1.0-M4.2" % "test",
  "com.google.code.javaparser" % "javaparser" % "1.0.8",
  "com.google.guava" % "guava" % "13.0.1",
  "com.google.inject" % "guice" % "3.0",
  "com.google.inject.extensions" % "guice-multibindings" % "3.0",
  "com.typesafe.akka" % "akka-actor" % "2.0.3",
  "com.typesafe.akka" % "akka-testkit" % "2.0.3" % "test",
  "com.tinkerpop.blueprints" % "blueprints-core" % "2.1.0",
  "junit" % "junit" % "4.10" % "test",
  "org.scalatest" %% "scalatest" % "2.0.M4" % "test",
  "org.scalaz" %% "scalaz-core" % "6.0.4",
  "org.mockito" % "mockito-all" % "1.9.0" % "test")

assemblySettings
