package fi.onesto.sbt.mobilizer

import java.io.{ByteArrayInputStream, InputStream}
import net.schmizz.sshj.xfer.InMemorySourceFile


object util {
  val configRepository = com.jcraft.jsch.OpenSSHConfig.parseFile("~/.ssh/config")
  val currentUser = Option(System.getProperty("user.name")).getOrElse("root")

  def usernameFor(hostname: String) = Option(configRepository.getConfig(hostname).getUser) getOrElse currentUser

  implicit final class Tap[A](val a: A) extends AnyVal {
    def tap[B](action: A => B): A = {
      action(a)
      a
    }
  }

  def shellEscape(s: String): String = {
    s.flatMap {
      case '"'     => "\\\""
      case '$'     => "\\$"
      case '!'     => "\\!"
      case '`'     => "\\`"
      case '\\'    => "\\\\"
      case c: Char => c.toString
    }
  }

  def shellQuote(s: String): String = "\"" + shellEscape(s) + "\""
  def shellQuote(command: String, args: String*): String = shellQuote(command +: args)
  def shellQuote(args: Seq[String]): String = args.map(shellQuote).mkString(" ")

  def stringSourceFile(path: String, content: String): InMemorySourceFile = {
    val contentBytes = content.getBytes
    new InMemorySourceFile {
      override def getLength: Long = contentBytes.size
      override def getName: String = path
      override def getInputStream: InputStream = new ByteArrayInputStream(contentBytes)
    }
  }
}
