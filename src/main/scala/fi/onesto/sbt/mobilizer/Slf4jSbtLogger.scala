package fi.onesto.sbt.mobilizer

import org.slf4j.helpers.MessageFormatter
import sbt.Level
import sbt.Level._
import org.slf4j.Marker


final class Slf4jSbtLogger(
    private[this] val underlying:   sbt.AbstractLogger,
    private[this] val name:         String,
    private[this] val minimumLevel: Level.Value)
  extends org.slf4j.helpers.MarkerIgnoringBase {

  private[this] final val serialVersionUID: Long = 1L

  private[this] def adjustLevel(level: Level.Value): Level.Value = {
    if (level.id >= minimumLevel.id) {
      level
    } else {
      Level.Debug
    }
  }

  private[this] def fmt(str: String, arg: Any): String = {
    val result = MessageFormatter.format(str, arg)
    Option(result.getThrowable).map{t => s"${result.getMessage}: $t"}.getOrElse(result.getMessage)
  }

  private[this] def fmt(str: String, arg1: Any, arg2: Any): String = {
    val result = MessageFormatter.format(str, arg1, arg2)
    Option(result.getThrowable).map{t => s"${result.getMessage}: $t"}.getOrElse(result.getMessage)
  }

  private[this] def fmt(str: String, args: Seq[AnyRef]): String = {
    val result = MessageFormatter.arrayFormat(str, args.toArray)
    Option(result.getThrowable).map{t => s"${result.getMessage}: $t"}.getOrElse(result.getMessage)
  }

  override def isTraceEnabled: Boolean                 = underlying.atLevel(Debug)
  override def isTraceEnabled(marker: Marker): Boolean = underlying.atLevel(Debug)

  override def trace(msg: String): Unit                             = underlying.log(adjustLevel(Debug), s"$name $msg")
  override def trace(msg: String, arg: AnyRef): Unit                = underlying.log(adjustLevel(Debug), s"$name ${fmt(msg, arg)}")
  override def trace(msg: String, arg1: AnyRef, arg2: AnyRef): Unit = underlying.log(adjustLevel(Debug), s"$name ${fmt(msg, arg1, arg2)}")
  override def trace(msg: String, arguments: AnyRef*): Unit         = underlying.log(adjustLevel(Debug), s"$name ${fmt(msg, arguments)}")
  override def trace(msg: String, t: Throwable): Unit               = underlying.log(adjustLevel(Debug), s"$name $msg: ${t.toString}")

  override def isDebugEnabled: Boolean                 = underlying.atLevel(Debug)
  override def isDebugEnabled(marker: Marker): Boolean = underlying.atLevel(Debug)

  override def debug(msg: String): Unit                             = underlying.log(adjustLevel(Debug), s"$name $msg")
  override def debug(msg: String, arg: AnyRef): Unit                = underlying.log(adjustLevel(Debug), s"$name ${fmt(msg, arg)}")
  override def debug(msg: String, arg1: AnyRef, arg2: AnyRef): Unit = underlying.log(adjustLevel(Debug), s"$name ${fmt(msg, arg1, arg2)}")
  override def debug(msg: String, arguments: AnyRef*): Unit         = underlying.log(adjustLevel(Debug), s"$name ${fmt(msg, arguments)}")
  override def debug(msg: String, t: Throwable): Unit               = underlying.log(adjustLevel(Debug), s"$msg: ${t.toString}")

  override def isInfoEnabled: Boolean                 = underlying.atLevel(Info)
  override def isInfoEnabled(marker: Marker): Boolean = underlying.atLevel(Info)

  override def info(msg: String): Unit                             = underlying.log(adjustLevel(Info), s"$name $msg")
  override def info(msg: String, arg: AnyRef): Unit                = underlying.log(adjustLevel(Info), s"$name ${fmt(msg, arg)}")
  override def info(msg: String, arg1: AnyRef, arg2: AnyRef): Unit = underlying.log(adjustLevel(Info), s"$name ${fmt(msg, arg1, arg2)}")
  override def info(msg: String, arguments: AnyRef*): Unit         = underlying.log(adjustLevel(Info), s"$name ${fmt(msg, arguments)}")
  override def info(msg: String, t: Throwable): Unit               = underlying.log(adjustLevel(Info), s"$msg: ${t.toString}")

  override def isWarnEnabled: Boolean                 = underlying.atLevel(Warn)
  override def isWarnEnabled(marker: Marker): Boolean = underlying.atLevel(Warn)

  override def warn(msg: String): Unit                             = underlying.log(adjustLevel(Warn), s"$name $msg")
  override def warn(msg: String, arg: AnyRef): Unit                = underlying.log(adjustLevel(Warn), s"$name ${fmt(msg, arg)}")
  override def warn(msg: String, arg1: AnyRef, arg2: AnyRef): Unit = underlying.log(adjustLevel(Warn), s"$name ${fmt(msg, arg1, arg2)}")
  override def warn(msg: String, arguments: AnyRef*): Unit         = underlying.log(adjustLevel(Warn), s"$name ${fmt(msg, arguments)}")
  override def warn(msg: String, t: Throwable): Unit               = underlying.log(adjustLevel(Warn), s"$msg: ${t.toString}")

  override def isErrorEnabled: Boolean                 = underlying.atLevel(Error)
  override def isErrorEnabled(marker: Marker): Boolean = underlying.atLevel(Error)

  override def error(msg: String): Unit                             = underlying.log(adjustLevel(Error), s"$name $msg")
  override def error(msg: String, arg: AnyRef): Unit                = underlying.log(adjustLevel(Error), s"$name ${fmt(msg, arg)}")
  override def error(msg: String, arg1: AnyRef, arg2: AnyRef): Unit = underlying.log(adjustLevel(Error), s"$name ${fmt(msg, arg1, arg2)}")
  override def error(msg: String, arguments: AnyRef*): Unit         = underlying.log(adjustLevel(Error), s"$name ${fmt(msg, arguments)}")
  override def error(msg: String, t: Throwable): Unit               = underlying.log(adjustLevel(Error), s"$msg: ${t.toString}")
}
