package fr.damienraymond.elasticstreamconsumption

import zio.ZIO
import zio.stream.ZStream

import scala.collection.SortedSet

trait IpsChangesWatcher {
  def watchIpsChanges: ZIO[Any, Throwable, ZStream[Any, Throwable, SortedSet[String]]]
}
