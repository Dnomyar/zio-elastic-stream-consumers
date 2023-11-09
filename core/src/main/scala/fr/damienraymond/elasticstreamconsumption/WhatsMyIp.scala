package fr.damienraymond.elasticstreamconsumption

import zio.ZIO

trait WhatsMyIp {
  def myIp: ZIO[Any, Throwable, String]
}
