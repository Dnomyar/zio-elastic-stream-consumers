package fr.damienraymond.elasticstreamconsumption

import zio.stream.ZStream
import zio.test.{Spec, TestClock, TestEnvironment, ZIOSpecDefault, assertTrue}
import zio.{Queue, Ref, Scope, ULayer, ZIO, ZLayer, durationInt}

object ElasticStreamConsumptionSpec extends ZIOSpecDefault {

  def createObservableStreams: ZIO[
    Any,
    Nothing,
    (Ref.Synchronized[Set[Int]], Queue[Int], Int => ZStream[Any, Nothing, Int])
  ] =
    for {
      queueWithMessages <- Ref.Synchronized.make[Set[Int]](Set.empty)
      streams <- Ref.Synchronized.make[Set[Queue[Int]]](
        Set.empty
      )
      queue <- Queue.unbounded[Int]

      _ <- ZStream
        .fromQueue(queue)
        .mapZIO(value =>
          for {
            values <- streams.get
            _ <- ZIO.foreachDiscard(values)(queue => queue.offer(value))
          } yield ()
        )
        .runDrain
        .forkDaemon

      mkStream = (number: Int) => {
        ZStream
          .unwrap(for {
            q <- Queue.unbounded[Int]
            s = ZStream
              .fromQueue(q)
              .tap(_ =>
                queueWithMessages
                  .update(store => store + number)
              )
            _ <- streams.update(_ + q)
          } yield s)

      }
    } yield (queueWithMessages, queue, mkStream)

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("ElasticStreamConsumption")(
      test("should start and stop stream") {
        ZIO.scoped(createObservableStreams.flatMap {
          case (ref, newMessageQueue, mkStream) =>
            for {
              ipsChangeQueue <- Queue.unbounded[List[String]]
              myIp = "1"
              consumers <- ZIO
                .serviceWithZIO[ElasticStreamConsumption[Int]](
                  _.elasticStreamConsumption(16, mkStream)
                )
                .provide(
                  ElasticStreamConsumption.live[Int],
                  createCluster(ipsChangeQueue)
                )

              _ <- consumers
                .mapZIOPar(20)(
                  _.mapZIOPar(20)(
                    _._2.runDrain
                  ).runDrain
                )
                .runDrain
                .forkDaemon
                .provideSome[Scope](createWhatsMyIp(myIp))

              // with one 1 IP
              _ <- ipsChangeQueue.offer(List(myIp))
              _ <- TestClock.adjust(2.seconds)
              _ <- newMessageQueue.offer(1)
              _ <- TestClock.adjust(2.seconds)
              count1 <- ref.get

              // with one 2 IPs
              _ <- ref.set(Set.empty)
              _ <- ipsChangeQueue.offer(List(myIp, "2"))
              _ <- TestClock.adjust(2.seconds)
              _ <- newMessageQueue.offer(1)
              _ <- TestClock.adjust(2.seconds)
              count2 <- ref.get

              // with one 3 IPs
              _ <- ref.set(Set.empty)
              _ <- ipsChangeQueue.offer(List(myIp, "2", "3"))
              _ <- TestClock.adjust(2.seconds)
              _ <- newMessageQueue.offer(1)
              _ <- TestClock.adjust(2.seconds)
              count3 <- ref.get

              // with one 1 IP
              _ <- ref.set(Set.empty)
              _ <- ipsChangeQueue.offer(List(myIp))
              _ <- TestClock.adjust(2.seconds)
              _ <- newMessageQueue.offer(1)
              _ <- TestClock.adjust(2.seconds)
              count4 <- ref.get

            } yield assertTrue(
              count1.size == 16 && count2.size == 8 && count3.size == 6 && count4.size == 16
            )
        })
      }
    )

  def createWhatsMyIp(ip: String): ULayer[WhatsMyIp] =
    ZLayer.succeed(new WhatsMyIp {
      override def myIp: ZIO[Any, Throwable, String] = ZIO.succeed(ip)
    })

  def createCluster(
      changes: Queue[List[String]]
  ): ZLayer[Any, Nothing, IpsChangesWatcher] = ZLayer.succeed(new IpsChangesWatcher {
    override def watchIpsChanges
        : ZIO[Any, Throwable, ZStream[Any, Throwable, List[String]]] =
      ZIO.succeed(ZStream.fromQueue(changes))
  })
}
