package fr.ipssi.healthmap.client.ui

import com.raquo.laminar.api.L.*

import fr.ipssi.healthmap.client.map.PointMap
import fr.ipssi.healthmap.shared.model.CommuneCount

/** Onglet « Carte » : filtre profession, métriques, couche de points du lot D et
  * classement des dix communes les mieux dotées.
  *
  * `PointMap` vient du lot D et n'est pas modifié : il consomme un
  * `Signal[List[MapPoint]]`, que l'onglet lui fournit depuis `AppState`.
  */
object MapTab:

  def apply(etat: AppState): HtmlElement =
    div(
      cls := "hm-tabcontent",
      h2("Carte de répartition des professionnels de santé"),
      ProfessionFilter(etat),
      metriques(etat),
      Widgets.chargement(etat.chargement),
      PointMap(etat.points.signal, heightPx = 620),
      Widgets.section(
        "Top 10 des communes les mieux dotées",
        "Cliquez sur un en-tête pour trier ; un second clic inverse le sens."
      ),
      DataTable[CommuneCount](
        lignes = etat.topCommunes.signal,
        colonnes = List(
          DataTable.texte[CommuneCount]("Commune", _.commune),
          DataTable.texte[CommuneCount]("Code postal", _.codePostal),
          DataTable.nombre[CommuneCount]("Professionnels", _.nombrePros)
        ),
        rang = true
      )
    )

  private def metriques(etat: AppState): HtmlElement =
    div(
      cls := "hm-metriques",
      Widgets.metrique(
        "Professionnels de santé",
        etat.effectifTotal.map(Format.entier),
        etat.libelleSelection
      ),
      Widgets.metrique(
        "Localisations",
        etat.localisations.map(Format.entier),
        Signal.fromValue("codes postaux distincts")
      )
    )
