import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

ThisBuild / scalaVersion := "3.3.4"
ThisBuild / organization := "fr.ipssi.healthmap"
ThisBuild / version      := "0.1.0-SNAPSHOT"

ThisBuild / scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Wunused:all"
)

val upickleV = "4.0.2"
val http4sV  = "0.23.29"
val laminarV = "17.2.0"
val domV     = "2.8.0"
val duckdbV  = "1.1.3"
val slf4jV   = "2.0.16"
val munitV   = "1.0.3"

// Répertoire dans lequel le linker Scala.js dépose le bundle servi par le serveur.
lazy val jsOutputDir = settingKey[File]("Répertoire de sortie du bundle Scala.js")
ThisBuild / jsOutputDir := (ThisBuild / baseDirectory).value / "static" / "js"

// ---------------------------------------------------------------------------
// shared : modèles, codecs et référentiels compilés pour la JVM et pour le navigateur
// ---------------------------------------------------------------------------
lazy val shared = crossProject(JVMPlatform, JSPlatform)
  .crossType(CrossType.Pure)
  .in(file("modules/shared"))
  .settings(
    name := "healthmap-shared",
    libraryDependencies ++= Seq(
      "com.lihaoyi"   %%% "upickle" % upickleV,
      "org.scalameta" %%% "munit"   % munitV % Test
    )
  )

lazy val sharedJVM = shared.jvm
lazy val sharedJS  = shared.js

// ---------------------------------------------------------------------------
// server : http4s-ember, DuckDB JDBC, proxy GeoJSON, service des assets
// ---------------------------------------------------------------------------
lazy val server = project
  .in(file("modules/server"))
  .enablePlugins(JavaAppPackaging)
  .dependsOn(sharedJVM)
  .settings(
    name := "healthmap-server",
    maintainer := "tissot.amaury@gmail.com",
    libraryDependencies ++= Seq(
      "org.http4s"    %% "http4s-ember-server" % http4sV,
      "org.http4s"    %% "http4s-ember-client" % http4sV,
      "org.http4s"    %% "http4s-dsl"          % http4sV,
      "org.slf4j"      % "slf4j-simple"        % slf4jV,
      "org.duckdb"     % "duckdb_jdbc"         % duckdbV,
      "org.scalameta" %% "munit"               % munitV % Test
    ),
    // Le serveur lit `static/` et `data/` à la racine du dépôt, pas dans modules/server.
    Compile / run / baseDirectory := (ThisBuild / baseDirectory).value,
    Compile / run / fork := true
  )

// ---------------------------------------------------------------------------
// client : Scala.js + Laminar
// ---------------------------------------------------------------------------
lazy val client = project
  .in(file("modules/client"))
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(sharedJS)
  .settings(
    name := "healthmap-client",
    scalaJSUseMainModuleInitializer := true,
    scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.NoModule)),
    Compile / fastLinkJS / scalaJSLinkerOutputDirectory := (ThisBuild / jsOutputDir).value,
    Compile / fullLinkJS / scalaJSLinkerOutputDirectory := (ThisBuild / jsOutputDir).value,
    libraryDependencies ++= Seq(
      "com.raquo"    %%% "laminar"     % laminarV,
      "org.scala-js" %%% "scalajs-dom" % domV
    )
  )

lazy val root = project
  .in(file("."))
  .aggregate(sharedJVM, sharedJS, server, client)
  .settings(
    name := "deserts-medicaux-scala",
    publish / skip := true
  )

// `sbt dev`  : compile le client puis démarre le serveur sur http://localhost:8080
// `sbt build`: bundle optimisé + archive distribuable dans modules/server/target/universal
addCommandAlias("dev", ";client/fastLinkJS;server/run")
addCommandAlias("build", ";client/fullLinkJS;server/Universal/packageBin")
