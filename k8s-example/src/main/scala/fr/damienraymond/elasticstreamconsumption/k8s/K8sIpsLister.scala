package fr.damienraymond.elasticstreamconsumption.k8s

import fr.damienraymond.elasticstreamconsumption.IpsChangesWatcher
import zio.{Ref, Schedule, ZIO, ZLayer, durationInt}
import io.kubernetes.client.openapi.apis.CoreV1Api
import io.kubernetes.client.openapi.{ApiClient, Configuration}
import io.kubernetes.client.util.ClientBuilder
import zio.stream.ZStream

import scala.collection.SortedSet
import scala.jdk.CollectionConverters.CollectionHasAsScala

object K8sIpsLister {

  val k8sClientLive: ZLayer[Any, Throwable, ApiClient] =
    ZLayer.fromZIO(ZIO.attemptBlocking(ClientBuilder.cluster().build()))

  val k8ApiLive: ZLayer[ApiClient, Throwable, CoreV1Api] = ZLayer.fromZIO(
    ZIO.serviceWithZIO[ApiClient](client =>
      ZIO.attempt {
        Configuration.setDefaultApiClient(client)
        new CoreV1Api()
      }
    )
  )

  def live(namespace: String): ZLayer[CoreV1Api, Nothing, IpsChangesWatcher] = {
    ZLayer.fromFunction((k8sApi: CoreV1Api) =>
      new IpsChangesWatcher {
        def getPodIps: ZIO[Any, Throwable, Set[String]] =
          for {
            pods <- ZIO.attemptBlocking(
              k8sApi
                .listNamespacedPod(
                  namespace,
                  null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  null,
                  null
                )
                .getItems
                .asScala
                .toSet
            )
          } yield pods.map(_.getStatus.getPodIP).filterNot(null == _)

        override def watchIpsChanges
            : ZIO[Any, Throwable, ZStream[Any, Throwable, SortedSet[String]]] =
          for {
            currentIps <- Ref.make[Set[String]](Set.empty)
          } yield ZStream
            .repeatZIOWithSchedule(
              getPodIps,
              Schedule.spaced(1.second)
            )
            .mapZIO(newIps =>
              currentIps.getAndSet(newIps).map(oldIps => (oldIps, newIps))
            )
            .collect {
              case (oldIps, newIps) if oldIps != newIps =>
                newIps.to(SortedSet)
            }
      }
    )
  }

}
