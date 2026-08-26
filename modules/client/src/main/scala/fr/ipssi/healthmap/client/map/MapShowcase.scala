package fr.ipssi.healthmap.client.map

import com.raquo.laminar.api.L.*

/** Vitrine de démonstration du lot D : les trois composants de carte réunis
  * derrière un filtre profession et une bascule d'onglets, alimentés par les
  * données simulées `SampleData`.
  *
  * Elle sert de banc d'essai autonome (aucun agrégat serveur requis) et
  * préfigure l'intégration côté lot E : chaque composant se monte quand son
  * onglet devient actif et se démonte proprement quand on le quitte.
  */
object MapShowcase:

  def apply(): HtmlElement =
    val selected  = Var(Set("Médecin"))
    val activeTab = Var(0)

    val points      = selected.signal.map(SampleData.mapPoints)
    val regions     = selected.signal.map(SampleData.byRegion)
    val departements = selected.signal.map(SampleData.byDepartement)

    div(
      filterBar(selected),
      child.text <-- selected.signal.map { sel =>
        val n = SampleData.mapPoints(sel).map(_.nombrePros).sum
        s"$n professionnels sur ${SampleData.mapPoints(sel).size} localisations (données simulées)."
      },
      tabBar(activeTab),
      child <-- activeTab.signal.map {
        case 0 => PointMap(points)
        case 1 => RegionChoropleth(regions)
        case _ => DepartementChoropleth(departements)
      }
    )

  /** Cases à cocher, une par profession du référentiel simulé. */
  private def filterBar(selected: Var[Set[String]]): HtmlElement =
    div(
      display := "flex",
      flexWrap := "wrap",
      gap := "8px",
      margin := "0 0 12px",
      SampleData.professions.map { p =>
        label(
          display := "inline-flex",
          alignItems := "center",
          gap := "6px",
          padding := "4px 10px",
          border := "1px solid var(--border, #ddd)",
          borderRadius := "999px",
          cursor := "pointer",
          fontSize := "14px",
          input(
            typ := "checkbox",
            checked <-- selected.signal.map(_.contains(p.nom)),
            onInput.mapToChecked --> { isChecked =>
              selected.update(s => if isChecked then s + p.nom else s - p.nom)
            }
          ),
          span(p.nom)
        )
      }
    )

  /** Bascule entre les trois cartes, à l'image des onglets de l'application. */
  private def tabBar(active: Var[Int]): HtmlElement =
    val labels = List("Carte des points", "Régions", "Départements")
    div(
      display := "flex",
      gap := "4px",
      margin := "0 0 12px",
      labels.zipWithIndex.map { (label, i) =>
        button(
          label,
          padding := "8px 14px",
          border := "1px solid var(--border, #ddd)",
          borderRadius := "8px",
          cursor := "pointer",
          fontSize := "14px",
          backgroundColor <-- active.signal.map(a => if a == i then "var(--accent, #b8562f)" else "transparent"),
          color <-- active.signal.map(a => if a == i then "#fff" else "inherit"),
          onClick.mapTo(i) --> active.writer
        )
      }
    )
