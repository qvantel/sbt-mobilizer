package fi.onesto.sbt

import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.connection.channel.direct.Session.Shell


package object mobilizer {
  import util._

  implicit class RichSSHClient(underlying: SSHClient) {
    def withSession[A](action: Session => A): A = {
      val session = underlying.startSession()
      try {
        action(session)
      }
      finally {
        session.close()
      }
    }

    def withShell[A](pty: Symbol = 'noPty)(action: Shell => A): A = {
      withSession { session =>
        if (pty != 'noPty)
          session.allocateDefaultPTY()
        action(session.startShell())
      }
    }

    def run(commandName: String, args: String*): Iterator[String] =
      runWithOptionalInput(commandName, None, args: _*)

    def runWithInput(commandName: String, input: String, args: String*): Iterator[String] =
      runWithOptionalInput(commandName, Some(input), args: _*)

    def runWithOptionalInput(commandName: String, inputOption: Option[String], args: String*): Iterator[String] = {
      withSession { session =>
        val command = session.exec(shellQuote(commandName, args: _*))
        inputOption foreach { input =>
          command.getOutputStream.write(input.getBytes)
          command.getOutputStream.close()
        }
        io.Source.fromInputStream(command.getInputStream).getLines() tap { output =>
          val errors = io.Source.fromInputStream(command.getErrorStream).getLines().mkString("\n")
          command.join()
          val exitStatus = command.getExitStatus
          if (exitStatus != 0)
            throw new CommandException(commandName, errors, exitStatus)
        }
      }
    }

    def mkdir(paths: String*) = run("mkdir", paths: _*)

    def mkdirWithParents(paths: String*) = run("mkdir", "-p" +: paths: _*)

    def symlink(source: String, destination: String) = run("ln", "-nsf", source, destination)

    def listFiles(): Iterator[String] = run("ls")

    def listFiles(path: String): Iterator[String] = run("ls", path)

    def createFile(path: String, content: String, mode: String = "0644") =
      runWithInput("sh", content, "-c", "cat > " + path + " && chmod " + mode + " " + path)

    def rmTree(path: String) =
      run("rm", "-rf", path)
  }
}
