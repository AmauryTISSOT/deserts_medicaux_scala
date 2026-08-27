package fr.ipssi.healthmap.client.ui

import com.raquo.laminar.api.L.*

import fr.ipssi.healthmap.client.map.{DepartementChoropleth, RegionChoropleth}
import fr.ipssi.healthmap.shared.model.{DepartementCount, RegionCount}

/** Onglet « Régions et départements » : les deux choroplèthes du lot D, leurs
  * tableaux triables et l'histogramme départemental.
  *
  * Le Python répétait ici le filtre profession dans un second `multiselect`
  * indépendant : les deux onglets pouvaient afficher des périmètres différents.
  * La sélection est désormais unique et partagée.
  */
object StatsTab:

  def apply(etat: AppState): HtmlElement =
    div(
      cls := "hm-tabcontent",
      h2("Répartition par région et par département"),
      ProfessionFilter(etat),
      Widgets.chargement(etat.chargement),
      Widgets.section("Professionnels de santé par région"),
      RegionChoropleth(etat.regions.signal, heightPx = 560),
      tableauRegions(etat),
      Widgets.section("Professionnels de santé par département"),
      DepartementChoropleth(etat.departements.signal, heightPx = 560),
      tableauDepartements(etat),
      Widgets.section(
        "Histogramme départemental",
        "Départements classés par effectif décroissant ; survolez une barre pour le détail."
      ),
      BarChart(histogramme(etat))
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
