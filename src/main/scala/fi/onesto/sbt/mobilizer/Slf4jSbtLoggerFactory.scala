package fi.onesto.sbt.mobilizer

import sbt.Level


final class Slf4jSbtLoggerFactory(
    private[this] val underlying:   sbt.AbstractLogger,
    private[this] val minimumLevel: Level.Value)
  extends org.slf4j.ILoggerFactory {
  override def getLogger(name: String): org.slf4j.Logger =
    new Slf4jSbtLogger(underlying, name, minimumLevel)
}
