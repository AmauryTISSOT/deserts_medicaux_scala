package fr.ipssi.healthmap.client

import com.raquo.laminar.api.L.*
import org.scalajs.dom

import fr.ipssi.healthmap.client.map.MapShowcase

/** Point d'entrée du client.
  *
  * En attendant la coquille à quatre onglets du lot E, `Main` affiche la vitrine
  * du lot D — les trois composants de carte (points, choroplèthe régionale,
  * choroplèthe départementale) sur données simulées. Le lot E remplacera ce
  * contenu par la structure d'onglets et y insèrera ces mêmes composants.
  */
object Main:

  def main(args: Array[String]): Unit =
    renderOnDomContentLoaded(dom.document.getElementById("app"), page())

  private def page(): HtmlElement =
    div(
      h1("HealthMap — cartographie"),
      p(cls := "lede", "Répartition des professionnels de santé en France (lot D)."),
      MapShowcase()
    )
