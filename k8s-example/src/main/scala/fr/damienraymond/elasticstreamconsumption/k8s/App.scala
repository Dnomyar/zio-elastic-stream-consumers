package fr.damienraymond.elasticstreamconsumption.k8s

import fr.damienraymond.elasticstreamconsumption.ElasticStreamConsumption
import zio.stream.ZStream
import zio.http._
import zio._

object App extends ZIOAppDefault {
  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] = ZIO
    .scoped(for {
      _ <- ZIO.succeed(println("Starting"))

      consumerFork <- consumers.fork
      serverFork <- Server.serve(k8sRoutes.toHttpApp).fork

      _ <- consumerFork.join
      _ <- serverFork.interrupt

      _ <- ZIO.succeed(println("Stopped"))
    } yield ())
    .mapError(error => ZIO.succeed(println(s"ERROR $error")))
    .provide(
      K8sIpsLister.k8sClientLive >>> K8sIpsLister.k8ApiLive >>> K8sIpsLister
        .live(
          "esc"
        ),
      K8sWhatsMyIp.live,
      ElasticStreamConsumption.live[Unit],
      Server.default
    )

  val consumers = for {
    elasticConsumer <- ZIO
      .serviceWithZIO[ElasticStreamConsumption[Unit]](
        _.elasticStreamConsumption(16, (_: Int) => ZStream.tick(10.seconds))
      )

    _ <- elasticConsumer
      .mapZIOPar(10)(
        _.mapZIOPar(20) { case (_, stream) =>
          stream.runDrain
        }.runDrain
      )
      .runDrain
  } yield ()

  val k8sRoutes: Routes[Any, Nothing] =
    Routes[Any, Nothing](
      Method.GET / "/" ->
        handler(Response.text("OK!")),
      Method.GET / "health" ->
        handler(Response.text("OK!")),
      Method.GET / "ready" ->
        handler(Response.text("Ready!"))
    )
}
