package fr.damienraymond.elasticstreamconsumption.k8s

import fr.damienraymond.elasticstreamconsumption.WhatsMyIp
import zio.{ULayer, ZIO, ZLayer}

object K8sWhatsMyIp {

  val live: ULayer[WhatsMyIp] = ZLayer.succeed(new WhatsMyIp {
    override def myIp: ZIO[Any, Throwable, String] = ZIO
      .fromOption(
        Option(System.getenv("MY_POD_IP"))
      )
      .orElseFail {
        new Throwable(
          s"MY_POD_IP env not found"
        )
      }
  })

}
