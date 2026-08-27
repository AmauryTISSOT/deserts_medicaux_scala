package fr.ipssi.healthmap.client.ui

import org.scalajs.dom
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.Thenable.Implicits.given
import upickle.default.{Reader, read, write}

import fr.ipssi.healthmap.shared.api.Endpoints
import fr.ipssi.healthmap.shared.model.*

/** Appels d'API typés de bout en bout.
  *
  * Les chemins viennent de `shared.api.Endpoints` et les corps sont décodés par
  * les codecs upickle de `shared.model` : le client et le serveur partagent donc
  * exactement les mêmes types, ce qui est l'intérêt principal du portage. Aucun
  * `js.Dynamic` ne remonte jusqu'aux composants.
  */
object ApiClient:

  /** Référentiel des professions et de leurs effectifs. */
  def professions: Future[List[Profession]] =
    lire[List[Profession]](Endpoints.professions)

  /** Agrégat par code postal, pour la couche de points du lot D. */
  def mapPoints(filtre: Seq[String]): Future[List[MapPoint]] =
    lire[List[MapPoint]](Endpoints.map(filtre))

  /** Agrégat par région, pour la choroplèthe régionale et son tableau. */
  def regions(filtre: Seq[String]): Future[List[RegionCount]] =
    lire[List[RegionCount]](Endpoints.regions(filtre))

  /** Agrégat par département, pour la choroplèthe, le tableau et l'histogramme. */
  def departements(filtre: Seq[String]): Future[List[DepartementCount]] =
    lire[List[DepartementCount]](Endpoints.departements(filtre))

  /** Classement des communes les mieux dotées. */
  def topCommunes(filtre: Seq[String], limite: Int): Future[List[CommuneCount]] =
    lire[List[CommuneCount]](Endpoints.topCommunes(filtre, limite))

  /** Analyse de couverture d'un département. */
  def coverage(code: String): Future[CoverageReport] =
    lire[CoverageReport](Endpoints.coverage(code))

  /** Question à l'assistant d'orientation. */
  def chat(requete: ChatRequest): Future[ChatResponse] =
    poster(Endpoints.chat, write(requete)).map(corps => read[ChatResponse](corps))

  // -------------------------------------------------------------------------

  private def lire[A: Reader](url: String): Future[A] =
    texte(url).map(corps => read[A](corps))

  /** GET renvoyant le corps en texte, en échec si le statut n'est pas 2xx.
    *
    * Le corps d'erreur est un `ApiError` sérialisé par le lot C : on tente de le
    * décoder pour remonter un message lisible plutôt qu'un code HTTP nu.
    */
  private def texte(url: String): Future[String] =
    dom.fetch(url).flatMap { reponse =>
      val corps: Future[String] = reponse.text()
      if reponse.ok then corps
      else corps.flatMap(c => Future.failed(new RuntimeException(echec(reponse.status, c))))
    }

  private def poster(url: String, corps: String): Future[String] =
    val options = js.Dynamic
      .literal(
        method = "POST",
        body = corps,
        headers = js.Dynamic.literal("Content-Type" -> "application/json")
      )
      .asInstanceOf[dom.RequestInit]

    dom.fetch(url, options).flatMap { reponse =>
      val recu: Future[String] = reponse.text()
      if reponse.ok then recu
      else recu.flatMap(c => Future.failed(new RuntimeException(echec(reponse.status, c))))
    }

  private def echec(statut: Int, corps: String): String =
    val detail =
      try read[ApiError](corps).message
      catch case _: Throwable => corps.take(160)
    if detail.trim.nonEmpty then detail else s"Erreur HTTP $statut"

  /** Message d'erreur présentable, quelle que soit la cause (réseau ou API). */
  def message(erreur: Throwable): String =
    Option(erreur.getMessage).filter(_.trim.nonEmpty).getOrElse(erreur.toString)
