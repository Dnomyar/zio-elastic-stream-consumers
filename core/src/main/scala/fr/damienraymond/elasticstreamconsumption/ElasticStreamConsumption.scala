package fr.damienraymond.elasticstreamconsumption

import izumi.reflect.Tag
import zio.{Chunk, Promise, Ref, Scope, UIO, ULayer, ZIO, ZLayer}
import zio.stream.ZStream

trait ElasticStreamConsumption[Message] {

  def elasticStreamConsumption(
      numberOfStreamsToConsumeFrom: Int,
      makeStreamZIO: Int => ZStream[Scope, Throwable, Message]
  ): ZIO[IpsChangesWatcher, Throwable, ZStream[
    WhatsMyIp,
    Throwable,
    ZStream[
      Any,
      Throwable,
      (Int, ZStream[Scope, Throwable, Message])
    ]
  ]]

  def getAssignments: UIO[Map[String, List[Int]]]
}

object ElasticStreamConsumption {
  private case class InterruptibleStream[Message](
      interruptionPromise: Promise[Throwable, Unit],
      stream: ZStream[Scope, Throwable, Message]
  )

  def live[Message: Tag]: ULayer[ElasticStreamConsumption[Message]] =
    ZLayer.fromZIO(
      for {
        podAssignments <- Ref.make(Map.empty[String, List[Int]])
      } yield new ElasticStreamConsumption[Message] {

        private type IP = String

        override def elasticStreamConsumption(
            numberOfStreams: Int,
            makeStreamZIO: Int => ZStream[Scope, Throwable, Message]
        ): ZIO[
          IpsChangesWatcher,
          Throwable,
          ZStream[WhatsMyIp, Throwable, ZStream[
            Any,
            Throwable,
            (Int, ZStream[Scope, Throwable, Message])
          ]]
        ] =
          for {
            currentAssignmentsRef <- Ref.Synchronized.make(
              Map.empty[Int, InterruptibleStream[Message]]
            )
            ipsUpdateStream <- ZIO.serviceWithZIO[IpsChangesWatcher](
              _.watchIpsChanges
            )
            assignmentUpdateStream =
              ipsUpdateStream.mapZIO(newIps =>
                for {
                  myIp <- ZIO.serviceWithZIO[WhatsMyIp](_.myIp)
                  groupSize =
                    if (newIps.isEmpty) numberOfStreams
                    else
                      Math
                        .ceil(
                          numberOfStreams.toDouble / newIps.size.toDouble
                        )
                        .toInt
                  allNewAssignmentsGrouped: Iterator[List[Int]] =
                    (1 to numberOfStreams).toList
                      .grouped(groupSize)
                  allNewAssignments: Map[IP, List[Int]] =
                    newIps.toList.zip(allNewAssignmentsGrouped).toMap
                  myNewAssignmentsNumbers <- ZIO
                    .fromOption(
                      allNewAssignments.get(myIp).map(_.toSet)
                    )
                    .mapError(error =>
                      new Throwable(
                        s"sub $myIp not found, error=$error"
                      )
                    )
                  currentAssignments <- currentAssignmentsRef.get
                  currentAssignmentsNumbers = currentAssignments.keySet
                  addedAssignmentNumbers = myNewAssignmentsNumbers.diff(
                    currentAssignmentsNumbers
                  )
                  addedSubscription <- ZIO.foreach(addedAssignmentNumbers) {
                    addedAssignmentNumber =>
                      val stream = makeStreamZIO(addedAssignmentNumber)
                      for {
                        streamInterruptPromise <- Promise.make[Throwable, Unit]
                        newInterruptibleStream = InterruptibleStream(
                          streamInterruptPromise,
                          stream.interruptWhen(streamInterruptPromise)
                        )
                        _ <- currentAssignmentsRef.update(
                          _ + (addedAssignmentNumber -> newInterruptibleStream)
                        )
                      } yield addedAssignmentNumber -> newInterruptibleStream.stream
                  }
                  removedAssignmentNumbers = currentAssignmentsNumbers.diff(
                    myNewAssignmentsNumbers
                  )
                  _ <- ZIO.foreachDiscard(removedAssignmentNumbers) {
                    removedAssignmentNumber =>
                      currentAssignmentsRef.updateZIO[Any, Throwable] {
                        currentAssignments =>
                          ZIO
                            .fromOption(
                              currentAssignments.get(removedAssignmentNumber)
                            )
                            .flatMap(_.interruptionPromise.succeed(()))
                            .as(
                              currentAssignments.removed(
                                removedAssignmentNumber
                              )
                            )
                            .mapError(error =>
                              new Throwable(
                                s"sub $removedAssignmentNumber not found, error=$error"
                              )
                            )
                      }
                  }
                  _ <- ZIO.succeed(
                    println(
                      s"ip=$myIp allIps=$newIps added=$addedAssignmentNumbers removed=$removedAssignmentNumbers"
                    )
                  )
                } yield ZStream.fromChunk(Chunk.from(addedSubscription))
              )

          } yield assignmentUpdateStream

        override def getAssignments: UIO[Map[IP, List[Int]]] =
          podAssignments.get
      }
    )

}
