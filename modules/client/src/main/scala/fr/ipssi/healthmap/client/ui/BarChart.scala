package fr.ipssi.healthmap.client.ui

import com.raquo.laminar.api.L.*

/** Histogramme en barres, équivalent du `px.bar` de `utils/charts.py`.
  *
  * Construit en HTML et CSS plutôt qu'avec une bibliothèque de graphiques : le
  * besoin — une barre par département, une infobulle, une échelle linéaire — ne
  * justifie ni la dépendance ni la façade Scala.js qui l'accompagnerait, et le
  * rendu suit le thème clair ou sombre de la page sans configuration.
  */
object BarChart:

  /** @param libelle texte sous la barre, court (un code de département)
    * @param detail  infobulle au survol
    * @param valeur  hauteur de la barre
    */
  final case class Bar(libelle: String, detail: String, valeur: Int)

  def apply(barres: Signal[List[Bar]], hauteurPx: Int = 260): HtmlElement =
    div(
      cls := "hm-barchart",
      div(
        cls := "hm-bars",
        height := s"${hauteurPx}px",
        children <-- barres.map { liste =>
          val maximum = liste.map(_.valeur).maxOption.getOrElse(0).max(1)
          liste.map(barre(_, maximum))
        }
      ),
      child.maybe <-- barres.map { liste =>
        Option.when(liste.isEmpty)(p(cls := "hm-muted", "Aucune donnée pour ce filtre."))
      }
    )

  private def barre(b: Bar, maximum: Int): HtmlElement =
    val brut        = math.max(1.0, b.valeur * 100.0 / maximum)
    val pourcentage = math.round(brut * 10) / 10.0
    div(
      cls := "hm-bar",
      title := b.detail,
      div(
        cls := "hm-bar-piste",
        div(cls := "hm-bar-remplissage", height := s"$pourcentage%")
      ),
      span(cls := "hm-bar-libelle", b.libelle)
    )
