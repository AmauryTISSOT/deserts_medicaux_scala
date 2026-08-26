package fr.ipssi.healthmap.server

import cats.effect.{ExitCode, IO, IOApp}
import cats.syntax.all.*
import com.comcast.ip4s.{Host, Port, host, port}
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.{ErrorAction, Logger}

import fr.ipssi.healthmap.server.api.ApiRoutes
import fr.ipssi.healthmap.server.chat.ChatService
import fr.ipssi.healthmap.server.data.{InMemoryProfessionalSource, ProfessionalSource}
import fr.ipssi.healthmap.server.geo.GeoJsonCache

/** Point d'entrée du serveur.
  *
  * La source de données est encore l'échantillon en mémoire : le lot B remplace
  * `InMemoryProfessionalSource.stub` par l'implémentation DuckDB sans toucher aux
  * routes, et le lot E remplace `ChatService.rulesBased` par le client Ollama.
  */
object Main extends IOApp:

  private val defaultHost: Host = host"0.0.0.0"
  private val defaultPort: Port = port"8080"

  def run(args: List[String]): IO[ExitCode] =
    val source: ProfessionalSource = InMemoryProfessionalSource.stub
    val chat: ChatService          = ChatService.rulesBased(source)
    val geo                        = GeoJsonCache()

    val routes = ApiRoutes(source, chat, geo) <+> StaticRoutes()

    val app = Logger.httpApp(logHeaders = false, logBody = false)(
      ErrorAction.httpApp(
        routes.orNotFound,
        (_, e) => IO.println(s"Erreur non rattrapée : ${e.getMessage}")
      )
    )

    for
      _ <- geo.prefetch.handleErrorWith(e =>
        IO.println(s"Fonds GeoJSON non préchargés (${e.getMessage}), ils seront retentés à la demande.")
      )
      _ <- IO.println(s"HealthMap démarre sur http://localhost:${defaultPort.value}")
      code <- EmberServerBuilder
        .default[IO]
        .withHost(bindHost(args))
        .withPort(bindPort(args))
        .withHttpApp(app)
        .build
        .useForever
        .as(ExitCode.Success)
    yield code

  private def bindHost(args: List[String]): Host =
    args.lift(0).flatMap(Host.fromString).getOrElse(defaultHost)

  private def bindPort(args: List[String]): Port =
    args.lift(1).flatMap(Port.fromString).getOrElse(defaultPort)
