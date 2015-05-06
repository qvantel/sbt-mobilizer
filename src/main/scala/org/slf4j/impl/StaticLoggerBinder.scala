package org.slf4j.impl

import fi.onesto.sbt.mobilizer.Slf4jSbtLoggerFactory
import org.slf4j.{ILoggerFactory, SneakySbtLoggerHelper}
import org.slf4j.helpers.NOPLoggerFactory
import org.slf4j.spi.LoggerFactoryBinder


final class StaticLoggerBinder(private[this] val sbtLoggerOption: Option[sbt.AbstractLogger]) extends LoggerFactoryBinder {
  private val loggerFactory: ILoggerFactory = {
    sbtLoggerOption map { sbtLogger =>
      new Slf4jSbtLoggerFactory(sbtLogger)
    } getOrElse {
      new NOPLoggerFactory
    }
  }

  override def getLoggerFactory = loggerFactory

  override def getLoggerFactoryClassStr = StaticLoggerBinder.loggerFactoryClassStr
}

object StaticLoggerBinder {
  private final var sbtLogger: Option[sbt.AbstractLogger] = None

  final def getSingleton: StaticLoggerBinder = new StaticLoggerBinder(sbtLogger)

  final val REQUESTED_API_VERSION: String = "1.6.99"

  private final val loggerFactoryClassStr: String = classOf[Slf4jSbtLoggerFactory].getName

  def startSbt(logger: sbt.AbstractLogger): Unit = {
    sbtLogger = Option(logger)
    SneakySbtLoggerHelper.reset()
  }
}
