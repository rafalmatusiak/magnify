import sbt._
import sbt.Keys._
import scala._
import Stream._

object ApplicationBuild extends Build {
  val appName         = "magnify"
  val appVersion      = "0.1.0-SNAPSHOT"

  val appDependencies =
    Seq(
      "com.google.code.javaparser" % "javaparser" % "1.0.8",
      "com.google.guava" % "guava" % "13.0.1",
      "com.google.inject" % "guice" % "3.0",
      "com.google.inject.extensions" % "guice-multibindings" % "3.0",
      "com.typesafe.akka" % "akka-actor_2.10" % "2.2.3",
      "com.tinkerpop.blueprints" % "blueprints" % "2.1.0",
      "com.tinkerpop.blueprints" % "blueprints-graph-jung" % "2.1.0",
      "com.tinkerpop.gremlin" % "gremlin-java" % "2.1.0",
      "net.sf.jung" % "jung-algorithms" % "2.0.1",
      "org.codehaus.javancss" % "javancss" % "32.53",
      "org.scalaz" %% "scalaz-core" % "6.0.4") ++
    Seq(  // test
      "com.typesafe.akka" % "akka-testkit_2.10" % "2.2.3" % "test",
      "junit" % "junit" % "4.11" % "test",
      "org.scalatest" %% "scalatest" % "2.0" % "test",
      "org.mockito" % "mockito-all" % "1.9.5" % "test") ++
    Seq(  // recoder
      "net.sf.retrotranslator" % "retrotranslator-runtime" % "1.2.9",
      "net.sf.retrotranslator" % "retrotranslator-transformer" % "1.2.9",
      "bsh" % "bsh" % "1.2b7",
      "backport-util-concurrent" % "backport-util-concurrent" % "3.1",
      "asm" % "asm-all" % "3.3"
    )

  private val jdkToolsJar: Option[File] = {
    def jdkToolsJar(home: File): Option[File] =
      ((home / "lib/tools.jar") #::
        (home / "../lib/tools.jar") #::
        (home / "Classes/classes.jar") #::
        (home / "../Classes/classes.jar") #:: Stream.empty[File])
        .find(_.exists())

    (for {
      homeOption <- sys.props.lift("java.home") #:: sys.env.lift("JAVA_HOME") #:: Stream.empty[Option[String]]
      home <- homeOption
      jdkToolsJar <- jdkToolsJar(file(home))
    } yield jdkToolsJar).headOption
  }


  val main = play.Project(appName, appVersion, appDependencies).settings(
    testOptions in Test := Nil,
    scalacOptions ++= Seq("-deprecation", "-unchecked"),
    resolvers ++= Seq(
      "Maven Central" at "http://repo1.maven.org/maven2",
      "Typesafe releases" at "http://repo.typesafe.com/typesafe/releases/",
      "OSS Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases/"),
    unmanagedJars in Compile := jdkToolsJar.fold(Seq[Attributed[File]]())(f => Seq(Attributed.blank(f))),
    unmanagedJars in Runtime := jdkToolsJar.fold(Seq[Attributed[File]]())(f => Seq(Attributed.blank(f)))
  )
}