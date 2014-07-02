package fi.onesto.sbt.mobilizer


class CommandException(name: String, message: String, exitStatus: Int)
  extends RuntimeException("Command " + name + " exited with status " + exitStatus + ": " + message)