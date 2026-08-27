package fr.ipssi.healthmap.server

import cats.effect.{ExitCode, IO, IOApp, Resource}
import cats.syntax.all.*
import com.comcast.ip4s.{Host, Port, host, port}
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Server
import org.http4s.server.middleware.{ErrorAction, Logger}

import fr.ipssi.healthmap.server.api.ApiRoutes
import fr.ipssi.healthmap.server.chat.{OllamaChatService, OllamaClient, OllamaConfig}
import fr.ipssi.healthmap.server.data.{ProfessionalRepository, ProfessionalSource}
import fr.ipssi.healthmap.server.geo.GeoJsonCache

/** Point d'entrée du serveur — fusion des lots B et E.
  *
  *   - Source de données : `ProfessionalRepository` (lot B), qui lit
  *     `data/fichier_professionnels_avec_coords.parquet` et
  *     `data/communes.parquet` (département/région issus de la jointure, jamais
  *     dérivés du code postal) ; les routes n'ont pas changé.
  *   - Assistant : `OllamaChatService` (lot E), adossé au client HTTP Ember. Si
  *     Ollama n'est pas lancé, il se replie sur `ChatService.rulesBased` : le
  *     serveur démarre et l'onglet assistant reste utilisable, avec un
  *     avertissement en tête de réponse.
  */
object Main extends IOApp:

  private val defaultHost: Host = host"0.0.0.0"
  private val defaultPort: Port = port"8080"

  private val professionalsPath = java.nio.file.Path.of("data", "fichier_professionnels_avec_coords.parquet")
  private val communesPath      = java.nio.file.Path.of("data", "communes.parquet")

  def run(args: List[String]): IO[ExitCode] =
    val geo    = GeoJsonCache()
    val ollama = OllamaConfig.fromEnv

    for
      source <- IO.blocking(ProfessionalRepository.load(professionalsPath, communesPath))
      _ <- IO.println(source.joinStats.toString)
      _ <- geo.prefetch.handleErrorWith(e =>
        IO.println(s"Fonds GeoJSON non préchargés (${e.getMessage}), ils seront retentés à la demande.")
      )
      _ <- IO.println(s"Assistant : Ollama sur ${ollama.baseUrl}, modèle ${ollama.model}")
      _ <- IO.println(s"HealthMap démarre sur http://localhost:${defaultPort.value}")
      _ <- serveur(args, source, geo, ollama).useForever
    yield ExitCode.Success

  /** Client HTTP puis serveur HTTP, tous deux libérés à l'arrêt. */
  private def serveur(
      args: List[String],
      source: ProfessionalSource,
      geo: GeoJsonCache,
      ollama: OllamaConfig
  ): Resource[IO, Server] =
    for
      clientHttp <- EmberClientBuilder.default[IO].build
      chat   = OllamaChatService(source, OllamaClient(clientHttp, ollama))
      routes = ApiRoutes(source, chat, geo) <+> StaticRoutes()
      app = Logger.httpApp(logHeaders = false, logBody = false)(
        ErrorAction.httpApp(
          routes.orNotFound,
          (_, e) => IO.println(s"Erreur non rattrapée : ${e.getMessage}")
        )
      )
      httpServeur <- EmberServerBuilder
        .default[IO]
        .withHost(bindHost(args))
        .withPort(bindPort(args))
        .withHttpApp(app)
        .build
    yield httpServeur

  private def bindHost(args: List[String]): Host =
    args.lift(0).flatMap(Host.fromString).getOrElse(defaultHost)

  private def bindPort(args: List[String]): Port =
    args.lift(1).flatMap(Port.fromString).getOrElse(defaultPort)
