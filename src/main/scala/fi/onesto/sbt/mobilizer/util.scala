package fi.onesto.sbt.mobilizer


object util {
  implicit class Tap[A](a: A) {
    def tap[B](action: A => B): A = {
      action(a)
      a
    }
  }

  def shellQuote(s: String): String = {
    "\"" + s.flatMap {
      case '"'     => "\\\""
      case '$'     => "\\$"
      case '!'     => "\\!"
      case '`'     => "\\`"
      case '\\'    => "\\\\"
      case c: Char => c.toString
    } + "\""
  }

  def shellQuote(command: String, args: String*): String = shellQuote(command +: args)
  def shellQuote(args: Seq[String]): String = args.map(shellQuote(_)).mkString(" ")

  val currentUser = Option(System.getProperty("user.name")).getOrElse("root")
}
