package fi.onesto.sbt.mobilizer

import sbt._


case class Rsync(
  commandPath: String      = Rsync.DefaultCommand,
  baseOptions: Seq[String] = Rsync.DefaultBaseOptions) {

  def apply(sources: Seq[String], target: String)(implicit log: Logger) {
    val command = commandPath +: (baseOptions ++ sources) :+ target
    log.debug(s"Running rsync command: $command")
    Process(command) ! log
  }

  def withLinkDest(sources: Seq[String], linkDest: Option[String], target: String)(implicit log: Logger) {
    val command = commandPath +: (baseOptions ++ linkDest.map("--link-dest=" + _).toList ++ sources) :+ target
    log.debug(s"Running rsync command: $command")
    Process(command) ! log
  }
}

object Rsync {
  val DefaultCommand: String = "rsync"

  val DefaultBaseOptions: Seq[String] = Seq(
    "--checksum",
    "--times",
    "--compress",
    "--human-readable")
}
