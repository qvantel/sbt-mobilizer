sbt-mobilizer
=============

This plugin copies the application, its dependencies and a startup script to a remote host.

The directory structure is similar to Capistrano:
* `${rootDirectory}`
    * `current` – link to the latest release directory
    * `releases`
        * `YYYYMMDDHHMMSS`
            * `lib` – dependency JARs
            * `${name}_${scalaVersion}-${version}.jar` – main application JAR
            * `${name}` – startup script

The startup script starts the application in the foreground.


Installation
------------

If you do not have it already, create a `project/project/Plugins.scala` with content like:
 
    import sbt._
    
    object Plugins extends Build {
      lazy val sbtMobilizerPlugin = uri("git://github.com/onesto/sbt-mobilizer.git")
      lazy val plugins = Project("plugins", file(".")).dependsOn(sbtMobilizerPlugin)
    }


Usage
-----

Add this to your `build.sbt`:

    // enable sbt-mobilizer
    deploySettings
    
    // if the project has multiple modules, you'll need to declare the
    // dependencies to other modules explicitly
    deployDependencies := Seq((Keys.`package` in Compile in sharedModule).value)
    
    // define deployment environments see 
    deployEnvironments := Map(
      'staging -> fi.onesto.sbt.mobilizer.DeploymentEnvironment(
        hosts         = Seq("staging.example.com"),
        rootDirectory = "/opt/myapp"),
      'production -> fi.onesto.sbt.mobilizer.DeploymentEnvironment(
        hosts         = Seq("production.example.com"),
        rootDirectory = "/opt/myapp"))

See [DeploymentEnvironment.scala](src/main/scala/fi/onesto/sbt/mobilizer/DeploymentEnvironment.scala) for a list of all settings and defaults.