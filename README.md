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

Add to your `project/plugins.sbt`:

    addSbtPlugin("fi.onesto.sbt" % "sbt-mobilizer" % "0.1.5")


Usage
-----

Enable the plugin according the [SBT documentation](http://www.scala-sbt.org/0.13/docs/Plugins.html).

An example:

    lazy val myProject = (project in file("."))
      .enablePlugins(Mobilizer)

Then add this to your `build.sbt`:

    // If there's some kind of revision information available, you can store it into
    // the application's root directory as a REVISION file.
    deployRevision := Option("1234")
    // You can use, for example https://github.com/onesto/sbt-buildnumber to get a revision identifier:
    //deployRevision := decoratedBuildNumber.value 

    // Define deployment environments.
    import fi.onesto.sbt.mobilizer.DeploymentEnvironment

    deployEnvironments := Map(
      'staging    -> DeploymentEnvironment(
        hosts         = Seq("staging.example.com"),
        rootDirectory = "/opt/myapp"),
      'production -> DeploymentEnvironment(
        hosts         = Seq("production.example.com"),
        rootDirectory = "/opt/myapp"))

See [DeploymentEnvironment.scala](src/main/scala/fi/onesto/sbt/mobilizer/DeploymentEnvironment.scala) for a list of all settings and defaults.

Deploy your code
----------------

To deploy your code use following syntax:

    sbt "deploy <environment name>"

For example:

    sbt "deploy production"
