package fr.ipssi.healthmap.server

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.http4s.client.Client
import org.http4s.{HttpApp, Response, Status}

import fr.ipssi.healthmap.server.chat.{OllamaChatService, OllamaClient, OllamaConfig}
import fr.ipssi.healthmap.server.data.InMemoryProfessionalSource
import fr.ipssi.healthmap.shared.model.{ChatRequest, ChatResponse}

/** Comportement de l'assistant selon la disponibilité d'Ollama.
  *
  * Le serveur Ollama est remplacé par un `Client` en mémoire : les tests
  * tournent sans modèle installé et sans accès réseau.
  */
class OllamaChatServiceSuite extends munit.FunSuite:

  private val source = InMemoryProfessionalSource.stub
  private val config = OllamaConfig.defaut

  /** Client qui répond comme Ollama, avec le texte fourni. */
  private def clientQuiRepond(texte: String): Client[IO] =
    val corps = ujson.write(ujson.Obj("response" -> texte))
    Client.fromHttpApp[IO](HttpApp[IO](_ => IO.pure(Response[IO](Status.Ok).withEntity(corps))))

  /** Client qui échoue, comme lorsque Ollama n'est pas lancé. */
  private val clientInjoignable: Client[IO] =
    Client.fromHttpApp[IO](HttpApp[IO](_ => IO.raiseError(new RuntimeException("connexion refusée"))))

  private def demander(client: Client[IO], message: String, departement: Option[String] = None): ChatResponse =
    val service = OllamaChatService(source, OllamaClient(client, config))
    service.respond(ChatRequest(message, departement)).unsafeRunSync()

  test("la réponse du modèle est reprise telle quelle, avec le contexte départemental") {
    val r = demander(clientQuiRepond("Consultez un dentiste."), "j'ai un mal de dents", Some("2A"))
    assert(r.reponse.contains("Consultez un dentiste."))
    assert(r.reponse.contains("Corse-du-Sud"))
    assertEquals(r.symptomes, List("mal de dents"))
    assertEquals(r.specialites, List("dentiste"))
    assert(!r.urgence)
  }

  test("Ollama injoignable : l'orientation par référentiel prend le relais") {
    val r = demander(clientInjoignable, "j'ai un mal de dents")
    assert(r.reponse.contains("Ollama non accessible"))
    assert(r.reponse.contains("dentiste"))
    assertEquals(r.specialites, List("dentiste"))
    assert(!r.urgence)
  }

  test("l'urgence court-circuite l'appel au modèle") {
    val r = demander(clientInjoignable, "je n'arrive plus à respirer")
    assert(r.urgence)
    assert(r.reponse.contains("15"))
    assert(!r.reponse.contains("Ollama"))
  }

  test("une réponse vide du modèle bascule sur le repli") {
    val r = demander(clientQuiRepond("   "), "j'ai mal au dos")
    assert(r.reponse.contains("réponse vide"))
    assertEquals(r.symptomes, List("dos"))
  }

  test("la configuration se surcharge par l'environnement, avec des valeurs par défaut sûres") {
    assertEquals(OllamaConfig.defaut.model, "mistral")
    assertEquals(OllamaConfig.defaut.baseUrl, "http://localhost:11434")
    assertEquals(OllamaConfig.fromEnv.baseUrl.endsWith("/"), false)
  }
