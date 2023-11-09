import Dependencies.*

ThisBuild / scalaVersion := "2.13.12"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "fr.damienraymond"
ThisBuild / organizationName := "zio-elastic-stream-consumption"

lazy val core = (project in file("core"))
  .settings(
    name := "core",
    libraryDependencies += zio,
    libraryDependencies += zioStreams,
    libraryDependencies += zioTest % Test
  )

lazy val `k8s-example` = (project in file("k8s-example"))
  .settings(
    name := "k8s",
    libraryDependencies += zio,
    libraryDependencies += zioStreams,
    libraryDependencies += zioHttp,
    libraryDependencies += zioTest % Test,
    libraryDependencies += k8s,
    mainClass := Some("fr.damienraymond.elasticstreamconsumption.k8s.App"),
    dockerExposedPorts := List(8080),
    dockerBaseImage := "openjdk:11",
    Docker / packageName := "zio-elastic-stream-consumption"
  )
  .enablePlugins(JavaAppPackaging, DockerPlugin)
  .dependsOn(core % "test->test;compile->compile")
  .aggregate(core)

lazy val root = (project in file("."))
  .aggregate(core, `k8s-example`)

// See https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for instructions on how to publish to Sonatype.
