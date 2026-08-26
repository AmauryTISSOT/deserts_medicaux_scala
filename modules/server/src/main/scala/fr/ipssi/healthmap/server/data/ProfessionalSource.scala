package fr.ipssi.healthmap.server.data

import fr.ipssi.healthmap.shared.model.*
import fr.ipssi.healthmap.shared.ref.Geo

/** Source des agrégats servis par l'API.
  *
  * Les méthodes sont synchrones et sans effet : conformément au plan, les agrégats
  * sont calculés une fois au démarrage, là où l'application Python rechargeait le
  * Parquet à chaque interaction. Le lot B fournit l'implémentation DuckDB ; le
  * filtre `professions` vide signifie « toutes professions ».
  */
trait ProfessionalSource:

  /** Professions du référentiel et leurs effectifs, triées par effectif décroissant. */
  def professions: List[Profession]

  /** Effectif total, tous filtres appliqués. */
  def total(filter: Set[String]): Int

  /** Agrégat par code postal, coordonnées GPS obligatoires. */
  def mapPoints(filter: Set[String]): List[MapPoint]

  /** Agrégat par région, clé `nom` alignée sur le GeoJSON des régions. */
  def byRegion(filter: Set[String]): List[RegionCount]

  /** Agrégat par département, clé `code` alignée sur le GeoJSON des départements. */
  def byDepartement(filter: Set[String]): List[DepartementCount]

  /** Communes les mieux dotées, tronquées à `limit`. */
  def topCommunes(filter: Set[String], limit: Int): List[CommuneCount]

  /** Analyse de couverture d'un département, `None` si le code est hors référentiel. */
  def coverage(code: String): Option[CoverageReport]

object ProfessionalSource:

  /** Seuils de qualification de la couverture, repris de `analyze_region_coverage`. */
  def niveau(total: Int): String =
    if total >= 1000 then "bien couvert"
    else if total >= 300 then "correctement couvert"
    else if total >= 100 then "sous-doté"
    else "désert médical"

  /** Construit un rapport de couverture à partir d'un décompte par profession. */
  def report(code: String, parProfession: List[Profession]): CoverageReport =
    val total = parProfession.map(_.effectif).sum
    CoverageReport(
      code = code,
      nom = Geo.departementName(code),
      region = Geo.regionOf(code),
      total = total,
      parProfession = parProfession.sortBy(-_.effectif),
      niveau = niveau(total)
    )
