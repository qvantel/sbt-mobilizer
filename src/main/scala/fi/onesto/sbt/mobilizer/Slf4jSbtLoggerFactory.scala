package fi.onesto.sbt.mobilizer


class Slf4jSbtLoggerFactory(private[this] val underlying: sbt.AbstractLogger) extends org.slf4j.ILoggerFactory {
  override def getLogger(name: String): org.slf4j.Logger = new Slf4jSbtLogger(underlying, name)
}
