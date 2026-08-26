package fr.ipssi.healthmap.client

import com.raquo.laminar.api.L.*
import org.scalajs.dom
import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js.Thenable.Implicits.given
import upickle.default.read

import fr.ipssi.healthmap.shared.api.Endpoints
import fr.ipssi.healthmap.shared.model.Profession

/** Squelette du client (lot A).
  *
  * Il vérifie la chaîne complète — compilation Scala.js, service du bundle, appel
  * d'API, décodage par les codecs de `shared`. Le lot E remplace ce contenu par la
  * structure à quatre onglets, le lot D y insère les cartes.
  */
object Main:

  private val professions = Var(Option.empty[List[Profession]])
  private val erreur      = Var(Option.empty[String])

  def main(args: Array[String]): Unit =
    renderOnDomContentLoaded(dom.document.getElementById("app"), page())

  private def page(): HtmlElement =
    div(
      onMountCallback(_ => charger()),
      h1("HealthMap"),
      p(cls := "lede", "Répartition des professionnels de santé en France."),
      child <-- erreur.signal.map(_.fold(emptyNode)(m => p(cls := "erreur", m))),
      child <-- professions.signal.map {
        case None       => p(cls := "lede", "Chargement du référentiel…")
        case Some(list) => tableau(list)
      }
    )

  private def tableau(list: List[Profession]): HtmlElement =
    div(
      p(cls := "lede", s"${list.size} professions, ${list.map(_.effectif).sum} professionnels."),
      ul(
        cls := "professions",
        list.map(p => li(span(p.nom), span(cls := "effectif", p.effectif.toString)))
      )
    )

  private def charger(): Unit =
    dom
      .fetch(Endpoints.professions)
      .flatMap(_.text())
      .map(body => professions.set(Some(read[List[Profession]](body))))
      .recover { case e => erreur.set(Some(s"API injoignable : ${e.getMessage}")) }
