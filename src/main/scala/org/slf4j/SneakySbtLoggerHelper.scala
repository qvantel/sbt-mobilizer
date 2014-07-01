package org.slf4j


object SneakySbtLoggerHelper {
  def reset() {
    LoggerFactory.reset()
  }
}
