import sbt._
import Keys._
import akka.sbt.AkkaKernelPlugin
import akka.sbt.AkkaKernelPlugin.{ Dist, outputDirectory, distJvmOptions}

object ProtoTruthBuild extends Build {
  val Organization = "noisycode"
  val Version = "1.0"
  val ScalaVersion = "2.10.3"

  lazy val ProtoTruthKernel  = Project(
    id = "akka-jdbc",
    base = file("."),
    settings = defaultSettings ++ AkkaKernelPlugin.distSettings ++ Seq(
      libraryDependencies ++= Dependencies.akkaJdbc,
      distJvmOptions in Dist := "-Xms1G -Xmx2G",
      outputDirectory in Dist := file("target/akka-jdbc")
    )
  )

  lazy val buildSettings = Defaults.defaultSettings ++ Seq(
    organization := Organization,
    version      := Version,
    scalaVersion := ScalaVersion,
    crossPaths   := false,
    organizationName := "noisycode",
    organizationHomepage := Some(url("http://noisycode.com"))
  )
  
  lazy val defaultSettings = buildSettings ++ Seq(
    // compile options
    scalacOptions ++= Seq("-encoding", "UTF-8", "-deprecation", "-unchecked"),
    javacOptions  ++= Seq("-Xlint:unchecked", "-Xlint:deprecation")
  )
}

object Dependencies {
  import Dependency._

  val akkaJdbc = Seq(akkaActor, akkaKernel, postgres, scalaTest, akkaTestkit)
}

object Dependency {
  val akkaVersion = "2.3.4"

  val akkaActor = "com.typesafe.akka" %% "akka-actor" % akkaVersion
  val akkaKernel = "com.typesafe.akka" %% "akka-kernel" % akkaVersion
  val postgres = "postgresql" % "postgresql" % "9.1-901-1.jdbc4"

  val scalaTest = "org.scalatest" %% "scalatest" %  "2.0"
  val akkaTestkit = "com.typesafe.akka" %% "akka-testkit" % akkaVersion
}
