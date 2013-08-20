package fi.onesto.sbt.mobilizer

import sbt._


case class NullLogger(
    getLevel:       Level.Value  = Level.Error,
    getTrace:       Int         = 0,
    successEnabled: Boolean     = false)
  extends AbstractLogger {

  def setLevel(newLevel: Level.Value) {}
  def setTrace(flag: Int) {}
  def setSuccessEnabled(flag: Boolean) {}
  def control(event: ControlEvent.Value, message: => String) {}
  def logAll(events: Seq[LogEvent]) {}
  def trace(t: => Throwable) {}
  def success(message: => String) {}
  def log(level: Level.Value, message: => String) {}
}
