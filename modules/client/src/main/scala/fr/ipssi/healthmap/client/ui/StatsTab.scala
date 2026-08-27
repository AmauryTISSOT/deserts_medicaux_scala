package fr.ipssi.healthmap.client.ui

import com.raquo.laminar.api.L.*

import fr.ipssi.healthmap.client.map.{Choropleth, ColorScale, DepartementChoropleth, RegionChoropleth}
import fr.ipssi.healthmap.shared.model.{DepartementCount, RegionCount}

/** Onglet « Régions et départements » : les deux choroplèthes du lot D, leurs
  * tableaux triables et l'histogramme départemental.
  *
  * Une bascule « Effectifs bruts / Densité pour 100 000 habitants » recolore les
  * deux cartes. C'est le point tranché en équipe : les effectifs bruts placent
  * Paris en tête parce qu'elle est peuplée ; la densité, rapportée à la
  * population INSEE, est la vraie mesure de désert médical.
  *
  * Le Python répétait ici le filtre profession dans un second `multiselect`
  * indépendant ; la sélection est désormais unique et partagée.
  */
object StatsTab:

  def apply(etat: AppState): HtmlElement =
    val densite = Var(false)

    val regionView: Signal[Choropleth.View] =
      etat.regions.signal.combineWith(etat.regionsDensity.signal, densite.signal).map {
        case (counts, stats, true) =>
          Choropleth.View(
            stats.map(d => Choropleth.Datum(d.nom, d.nom, d.pour100k)),
            "Densité /100 000 hab.",
            ColorScale.densite
          )
        case (counts, _, false) =>
          Choropleth.View(
            counts.map(c => Choropleth.Datum(c.nom, c.nom, c.nombrePros.toDouble)),
            "Pros par région",
            ColorScale.entier
          )
      }

    val departementView: Signal[Choropleth.View] =
      etat.departements.signal.combineWith(etat.departementsDensity.signal, densite.signal).map {
        case (counts, stats, true) =>
          Choropleth.View(
            stats.map(d => Choropleth.Datum(d.code, s"${d.nom} (${d.code})", d.pour100k)),
            "Densité /100 000 hab.",
            ColorScale.densite
          )
        case (counts, _, false) =>
          Choropleth.View(
            counts.map(c => Choropleth.Datum(c.code, s"${c.nom} (${c.code})", c.nombrePros.toDouble)),
            "Pros par département",
            ColorScale.entier
          )
      }

    div(
      cls := "hm-tabcontent",
      h2("Répartition par région et par département"),
      ProfessionFilter(etat),
      Widgets.chargement(etat.chargement),
      bascule(densite),
      Widgets.section("Professionnels de santé par région"),
      RegionChoropleth(regionView, heightPx = 560),
      tableauRegions(etat),
      Widgets.section("Professionnels de santé par département"),
      DepartementChoropleth(departementView, heightPx = 560),
      tableauDepartements(etat),
      Widgets.section(
        "Histogramme départemental",
        "Départements classés par effectif décroissant ; survolez une barre pour le détail."
      ),
      BarChart(histogramme(etat))
    )

  /** Bascule « Effectifs / Densité » qui recolore les deux choroplèthes. */
  private def bascule(densite: Var[Boolean]): HtmlElement =
    div(
      cls := "hm-mesure",
      div(
        cls := "hm-mesure-tete",
        span(cls := "hm-mesure-label", "Colorer les cartes par"),
        div(
          cls := "hm-segmente",
          bouton(densite, actif = false, "Effectifs bruts"),
          bouton(densite, actif = true, "Densité / 100 000 hab.")
        )
      ),
      p(
        cls := "hm-muted hm-note",
        child.text <-- densite.signal.map { d =>
          if d then
            "Professionnels résolus pour 100 000 habitants (population INSEE) : la mesure de désert médical, " +
              "indépendante de la taille de la population."
          else
            "Effectifs bruts : une zone peuplée ressort mécaniquement en tête. Basculez en densité pour corriger ce biais."
        }
      )
    )

  private def bouton(densite: Var[Boolean], actif: Boolean, texte: String): HtmlElement =
    button(
      cls := "hm-segmente-btn",
      typ := "button",
      cls.toggle("est-actif") <-- densite.signal.map(_ == actif),
      texte,
      onClick --> Observer[Any](_ => densite.set(actif))
    )

  private def tableauRegions(etat: AppState): HtmlElement =
    val total = etat.regions.signal.map(_.map(_.nombrePros).sum)
    div(
      Widgets.section("Tableau par région"),
      DataTable[RegionCount](
        lignes = etat.regions.signal,
        colonnes = List(
          DataTable.texte[RegionCount]("Région", _.nom),
          DataTable.nombre[RegionCount]("Professionnels", _.nombrePros)
        ),
        rang = true
      ),
      p(
        cls := "hm-muted hm-note",
        child.text <-- total.map(t => s"Total réparti sur les régions : ${Format.entier(t)} professionnels.")
      )
    )

  private def tableauDepartements(etat: AppState): HtmlElement =
    div(
      Widgets.section("Tableau par département"),
      DataTable[DepartementCount](
        lignes = etat.departements.signal,
        colonnes = List(
          DataTable.texte[DepartementCount]("Code", _.code),
          DataTable.texte[DepartementCount]("Département", _.nom),
          DataTable.texte[DepartementCount]("Région", _.region),
          DataTable.nombre[DepartementCount]("Professionnels", _.nombrePros)
        ),
        rang = true
      )
    )

  /** Une barre par département, triée par effectif décroissant. */
  private def histogramme(etat: AppState): Signal[List[BarChart.Bar]] =
    etat.departements.signal.map { liste =>
      liste
        .sortBy(-_.nombrePros)
        .map(d =>
          BarChart.Bar(
            libelle = d.code,
            detail = s"${d.nom} (${d.code}) - ${d.region} - ${Format.entier(d.nombrePros)} professionnels",
            valeur = d.nombrePros
          )
        )
    }
