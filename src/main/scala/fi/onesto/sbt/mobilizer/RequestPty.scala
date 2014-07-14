package fi.onesto.sbt.mobilizer


sealed trait RequestPty
case object WithPty extends RequestPty
case object NoPty   extends RequestPty