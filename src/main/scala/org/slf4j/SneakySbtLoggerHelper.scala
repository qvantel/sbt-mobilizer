package org.slf4j


object SneakySbtLoggerHelper {
  def reset(): Unit = {
    LoggerFactory.reset()
  }
}
