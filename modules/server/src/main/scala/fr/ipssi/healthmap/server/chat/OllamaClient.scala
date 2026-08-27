package fr.ipssi.healthmap.server.chat

import cats.effect.IO
import cats.syntax.all.*
import org.http4s.client.Client
import org.http4s.headers.`Content-Type`
import org.http4s.{MediaType, Method, Request, Uri}

import scala.concurrent.duration.*

/** Paramètres du serveur Ollama local. */
final case class OllamaConfig(baseUrl: String, model: String, timeout: FiniteDuration)

object OllamaConfig:

  /** Valeurs de l'application Python : Ollama local, modèle `mistral`, 30 secondes. */
  val defaut: OllamaConfig = OllamaConfig("http://localhost:11434", "mistral", 30.seconds)

  /** Surcharge par variables d'environnement, pour la démonstration comme pour
    * les postes où Ollama écoute ailleurs.
    */
  def fromEnv: OllamaConfig =
    OllamaConfig(
      baseUrl = sys.env.getOrElse("HEALTHMAP_OLLAMA_URL", defaut.baseUrl).stripSuffix("/"),
      model = sys.env.getOrElse("HEALTHMAP_OLLAMA_MODEL", defaut.model),
      timeout = sys.env
        .get("HEALTHMAP_OLLAMA_TIMEOUT_S")
        .flatMap(_.toIntOption)
        .filter(_ > 0)
        .map(_.seconds)
        .getOrElse(defaut.timeout)
    )

/** Client HTTP vers l'API `/api/generate` d'Ollama.
  *
  * Porté depuis `HealthMapChatbot._query_ollama`. Deux différences assumées :
  * l'indisponibilité remonte dans un `Left` typé au lieu d'être renvoyée comme
  * si c'était une réponse du modèle, et le délai maximal est appliqué par
  * `IO.timeout` plutôt que par la bibliothèque HTTP — le comportement est donc
  * le même quel que soit le point de blocage.
  */
final class OllamaClient(client: Client[IO], val config: OllamaConfig):

  /** Réponse du modèle, ou message d'indisponibilité présentable à l'utilisateur. */
  def generate(prompt: String): IO[Either[String, String]] =
    Uri.fromString(s"${config.baseUrl}/api/generate") match
      case Left(_) =>
        IO.pure(Left(s"URL Ollama invalide : ${config.baseUrl}"))
      case Right(uri) =>
        val corps = ujson.write(
          ujson.Obj(
            "model"  -> config.model,
            "prompt" -> prompt,
            "stream" -> false
          )
        )
        val requete = Request[IO](Method.POST, uri)
          .withEntity(corps)
          .withContentType(`Content-Type`(MediaType.application.json))

        client
          .expect[String](requete)
          .timeout(config.timeout)
          .map(extraire)
          .handleError(_ => Left(indisponible))

  /** Message d'indisponibilité, calqué sur celui du Python. */
  val indisponible: String =
    s"Serveur Ollama non accessible. Vérifiez qu'Ollama est lancé sur ${config.baseUrl} " +
      s"et que le modèle ${config.model} est installé."

  private def extraire(corps: String): Either[String, String] =
    try
      val texte = ujson.read(corps).obj.get("response").map(_.str).getOrElse("").trim
      if texte.isEmpty then Left("Le modèle Ollama a renvoyé une réponse vide.") else Right(texte)
    catch case e: Throwable => Left(s"Réponse Ollama illisible : ${e.getMessage}")

object OllamaClient:

  def apply(client: Client[IO], config: OllamaConfig): OllamaClient =
    new OllamaClient(client, config)
