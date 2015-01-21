import bintray.Keys._

sbtPlugin := true

scalaVersion := "2.10.4"

sbtVersion := "0.13.5"

name := "sbt-mobilizer"

version := "0.0.5"

organization := "fi.onesto.sbt"

organizationName := "Onesto Services Oy"

organizationHomepage := Some(new java.net.URL("http://onesto.fi"))

description := "Deployment plugin for SBT"

startYear := Some(2013)

homepage := Some(url("https://github.com/onesto/sbt-mobilizer"))

scmInfo := Some(ScmInfo(new java.net.URL("https://github.com/onesto/sbt-mobilizer"), "scm:git:github.com/onesto/sbt-mobilizer.git", Some("scm:git:git@github.com:onesto/sbt-mobilizer.git")))

bintrayPublishSettings

publishMavenStyle := false

publishArtifact in Test := false

repository in bintray := "sbt-plugins"

bintrayOrganization in bintray := Some("onesto")

licenses += ("MIT", url("https://github.com/onesto/sbt-mobilizer/blob/master/LICENSE"))


net.virtualvoid.sbt.graph.Plugin.graphSettings


testOptions in Test += Tests.Argument("-oD")

javacOptions in ThisBuild ++= Seq(
  "-deprecation",
  "-g",
  "-source", "7",
  "-Xlint")

scalacOptions in ThisBuild ++= Seq(
  "-unchecked",
  "-deprecation",
  "-feature",
  //"-explaintypes",
  //"-optimise",
  "-target:jvm-1.7",
  //"-Xcheck-null",
  "-Xcheckinit",
  "-Xlint",
  "-Yclosure-elim",
  "-Ydead-code",
  "-Yinline",
  "-Yinline-handlers",
  "-Ywarn-all",
  "-Xlog-reflective-calls",
  "-Xmax-classfile-name", "130"  // avoid problems on eCryptFS
)

libraryDependencies ++= Seq(
  "com.github.nscala-time" %% "nscala-time"                 % "1.4.0",
  "org.bouncycastle"        % "bcprov-jdk15on"              % "1.51",
  "com.jcraft"              % "jzlib"                       % "1.1.3",
  "org.slf4j"               % "slf4j-api"                   % "1.7.7",
  "net.schmizz"             % "sshj"                        % "0.10.0",
  "com.jcraft"              % "jsch.agentproxy.usocket-jna" % "0.0.8",
  "com.jcraft"              % "jsch.agentproxy.sshagent"    % "0.0.8",
  "com.jcraft"              % "jsch.agentproxy.pageant"     % "0.0.8",
  "com.jcraft"              % "jsch.agentproxy.sshj"        % "0.0.8" excludeAll(ExclusionRule(organization = "net.schmizz")),
  "commons-pool"            % "commons-pool"                % "1.6"
)
