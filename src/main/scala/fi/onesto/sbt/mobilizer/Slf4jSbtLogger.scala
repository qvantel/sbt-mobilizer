package fi.onesto.sbt.mobilizer

import org.slf4j.helpers.MessageFormatter
import sbt.Level._
import org.slf4j.Marker


final class Slf4jSbtLogger(
    private[this] val underlying: sbt.AbstractLogger,
    private[this] val name: String)
  extends org.slf4j.helpers.MarkerIgnoringBase {

  private[this] final val serialVersionUID: Long = 1L

  override def isTraceEnabled: Boolean                 = underlying.atLevel(Debug)
  override def isTraceEnabled(marker: Marker): Boolean = underlying.atLevel(Debug)

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

  override def trace(msg: String): Unit                             = underlying.debug(s"$name $msg")
  override def trace(msg: String, arg: AnyRef): Unit                = underlying.debug(s"$name ${fmt(msg, arg)}")
  override def trace(msg: String, arg1: AnyRef, arg2: AnyRef): Unit = underlying.debug(s"$name ${fmt(msg, arg1, arg2)}")
  override def trace(msg: String, arguments: AnyRef*): Unit         = underlying.debug(s"$name ${fmt(msg, arguments)}")
  override def trace(msg: String, t: Throwable): Unit               = underlying.debug(s"$name $msg: ${t.toString}")

  override def isDebugEnabled: Boolean                 = underlying.atLevel(Debug)
  override def isDebugEnabled(marker: Marker): Boolean = underlying.atLevel(Debug)

  override def debug(msg: String): Unit                             = underlying.debug(s"$name $msg")
  override def debug(msg: String, arg: AnyRef): Unit                = underlying.debug(s"$name ${fmt(msg, arg)}")
  override def debug(msg: String, arg1: AnyRef, arg2: AnyRef): Unit = underlying.debug(s"$name ${fmt(msg, arg1, arg2)}")
  override def debug(msg: String, arguments: AnyRef*): Unit         = underlying.debug(s"$name ${fmt(msg, arguments)}")
  override def debug(msg: String, t: Throwable): Unit               = underlying.debug(s"$msg: ${t.toString}")

  override def isInfoEnabled: Boolean                 = underlying.atLevel(Info)
  override def isInfoEnabled(marker: Marker): Boolean = underlying.atLevel(Info)

  override def info(msg: String): Unit                             = underlying.info(s"$name $msg")
  override def info(msg: String, arg: AnyRef): Unit                = underlying.info(s"$name ${fmt(msg, arg)}")
  override def info(msg: String, arg1: AnyRef, arg2: AnyRef): Unit = underlying.info(s"$name ${fmt(msg, arg1, arg2)}")
  override def info(msg: String, arguments: AnyRef*): Unit         = underlying.info(s"$name ${fmt(msg, arguments)}")
  override def info(msg: String, t: Throwable): Unit               = underlying.info(s"$msg: ${t.toString}")

  override def isWarnEnabled: Boolean                 = underlying.atLevel(Warn)
  override def isWarnEnabled(marker: Marker): Boolean = underlying.atLevel(Warn)

  override def warn(msg: String): Unit                             = underlying.warn(s"$name $msg")
  override def warn(msg: String, arg: AnyRef): Unit                = underlying.warn(s"$name ${fmt(msg, arg)}")
  override def warn(msg: String, arg1: AnyRef, arg2: AnyRef): Unit = underlying.warn(s"$name ${fmt(msg, arg1, arg2)}")
  override def warn(msg: String, arguments: AnyRef*): Unit         = underlying.warn(s"$name ${fmt(msg, arguments)}")
  override def warn(msg: String, t: Throwable): Unit               = underlying.warn(s"$msg: ${t.toString}")

  override def isErrorEnabled: Boolean                 = underlying.atLevel(Error)
  override def isErrorEnabled(marker: Marker): Boolean = underlying.atLevel(Error)

  override def error(msg: String): Unit                             = underlying.error(s"$name $msg")
  override def error(msg: String, arg: AnyRef): Unit                = underlying.error(s"$name ${fmt(msg, arg)}")
  override def error(msg: String, arg1: AnyRef, arg2: AnyRef): Unit = underlying.error(s"$name ${fmt(msg, arg1, arg2)}")
  override def error(msg: String, arguments: AnyRef*): Unit         = underlying.error(s"$name ${fmt(msg, arguments)}")
  override def error(msg: String, t: Throwable): Unit               = underlying.error(s"$msg: ${t.toString}")
}
