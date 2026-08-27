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
import fr.ipssi.healthmap.server.data.{InMemoryProfessionalSource, ProfessionalSource}
import fr.ipssi.healthmap.server.geo.GeoJsonCache

/** Point d'entrée du serveur.
  *
  * La source de données est encore l'échantillon en mémoire : le lot B remplace
  * `InMemoryProfessionalSource.stub` par l'implémentation DuckDB sans toucher aux
  * routes.
  *
  * L'assistant est désormais `OllamaChatService`, adossé au client HTTP Ember.
  * Si Ollama n'est pas lancé, il se replie sur `ChatService.rulesBased` : le
  * serveur démarre et l'onglet assistant reste utilisable, avec un avertissement
  * en tête de réponse.
  */
object Main extends IOApp:

  private val defaultHost: Host = host"0.0.0.0"
  private val defaultPort: Port = port"8080"

  def run(args: List[String]): IO[ExitCode] =
    val source: ProfessionalSource = InMemoryProfessionalSource.stub
    val geo                        = GeoJsonCache()
    val ollama                     = OllamaConfig.fromEnv

    for
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
