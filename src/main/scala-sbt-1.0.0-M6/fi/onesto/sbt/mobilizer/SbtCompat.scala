package fi.onesto.sbt.mobilizer

import sbt.File


object SbtCompat {
  val isArchive = sbt.internal.inc.classpath.ClasspathUtilities.isArchive(_: File)
}
