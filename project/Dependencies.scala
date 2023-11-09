import sbt._

object Dependencies {
  private val zioVersion = "2.0.19"

  lazy val zio = "dev.zio" %% "zio" % zioVersion
  lazy val zioStreams = "dev.zio" %% "zio-streams" % zioVersion
  lazy val zioHttp = "dev.zio" %% "zio-http" % "3.0.0-RC3"
  lazy val zioTest = "dev.zio" %% "zio-test" % zioVersion

  lazy val k8s = "io.kubernetes" % "client-java" % "18.0.0"

}
