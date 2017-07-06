package fi.onesto.sbt

import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.IOUtils
import net.schmizz.sshj.connection.channel.direct.Session


package object mobilizer {
  import util._

  implicit final class SSHClientHelpers(val underlying: SSHClient) extends AnyVal {
    def withSession[A](action: Session => A): A = {
      val session = underlying.startSession()
      try {
        action(session)
      } finally {
        session.close()
      }
    }

    def run(commandName: String, args: String*): Iterator[String] =
      runWithOptionalInput(commandName, None, args: _*)

    def runShAndDiscard(commandLine: String, requestPty: RequestPty = NoPty): Unit = {
      withSession { session =>
        if (requestPty == WithPty)
          session.allocateDefaultPTY()
        val command = session.exec(commandLine)
        command.getOutputStream.close()
        val errors = IOUtils.readFully(command.getErrorStream).toString
        IOUtils.readFully(command.getInputStream)
        command.getInputStream.close()
        command.join()
        val exitStatus = command.getExitStatus
        if (exitStatus != 0)
          throw new CommandException(commandLine, errors, exitStatus)
      }
    }

    def runAndDiscard(commandName: String, args: String*): Unit = {
      runWithOptionalInputAndDiscard(commandName, None, args: _*)
    }

    def runWithInput(commandName: String, input: String, args: String*): Iterator[String] =
      runWithOptionalInput(commandName, Option(input), args: _*)

    def runWithInputAndDiscard(commandName: String, input: String, args: String*): Unit = {
      runWithOptionalInputAndDiscard(commandName, Option(input), args: _*)
    }

    def runWithOptionalInput(commandName: String, inputOption: Option[String], args: String*): Iterator[String] = {
      withSession { session =>
        val command = session.exec(shellQuote(commandName, args: _*))
        inputOption foreach { input =>
          command.getOutputStream.write(input.getBytes)
          command.getOutputStream.flush()
        }
        command.getOutputStream.close()
        val output = IOUtils.readFully(command.getInputStream).toString.lines
        val errors = IOUtils.readFully(command.getErrorStream).toString
        command.join()
        val exitStatus = command.getExitStatus
        if (exitStatus != 0)
          throw new CommandException(commandName, errors, exitStatus)
        output
      }
    }

    def runWithOptionalInputAndDiscard(commandName: String, inputOption: Option[String], args: String*): Unit = {
      withSession { session =>
        val command = session.exec(shellQuote(commandName, args: _*))
        inputOption foreach { input =>
          command.getOutputStream.write(input.getBytes)
          command.getOutputStream.flush()
        }
        command.getOutputStream.close()
        val errors = IOUtils.readFully(command.getErrorStream).toString
        IOUtils.readFully(command.getInputStream)
        command.getInputStream.close()
        command.join()
        val exitStatus = command.getExitStatus
        if (exitStatus != 0)
          throw new CommandException(commandName, errors, exitStatus)
      }
    }

    def symlink(source: String, destination: String): Unit = {
      runAndDiscard("ln", "-nsf", source, destination)
    }

    def rmTree(path: String): Unit = {
      runAndDiscard("rm", "-rf", path)
    }
  }
}
