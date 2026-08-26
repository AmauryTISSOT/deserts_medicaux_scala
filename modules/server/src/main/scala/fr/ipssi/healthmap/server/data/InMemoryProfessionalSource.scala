package fr.ipssi.healthmap.server.data

import fr.ipssi.healthmap.shared.model.*
import fr.ipssi.healthmap.shared.ref.Geo

/** Une ligne agrégée : un code postal, une profession, un effectif. */
case class Row(
    codePostal: String,
    commune: String,
    latitude: Double,
    longitude: Double,
    profession: String,
    effectif: Int
)

/** Implémentation de référence des agrégats, en mémoire.
  *
  * Sert de source factice tant que le lot B n'a pas branché DuckDB, et de
  * définition exécutable du comportement attendu : les tests du lot B peuvent
  * comparer les agrégats SQL à ceux-ci sur un échantillon.
  */
class InMemoryProfessionalSource(rows: List[Row]) extends ProfessionalSource:

  private def keep(filter: Set[String]): List[Row] =
    if filter.isEmpty then rows else rows.filter(r => filter.contains(r.profession))

  val professions: List[Profession] =
    rows
      .groupMapReduce(_.profession)(_.effectif)(_ + _)
      .toList
      .map(Profession.apply)
      .sortBy(p => (-p.effectif, p.nom))

  def total(filter: Set[String]): Int =
    keep(filter).map(_.effectif).sum

  def mapPoints(filter: Set[String]): List[MapPoint] =
    keep(filter)
      .groupBy(r => (r.codePostal, r.commune, r.latitude, r.longitude))
      .toList
      .map { case ((cp, commune, lat, lon), rs) =>
        MapPoint(cp, commune, lat, lon, rs.map(_.effectif).sum, rs.map(_.profession).distinct.sorted)
      }
      .sortBy(_.codePostal)

  def byRegion(filter: Set[String]): List[RegionCount] =
    keep(filter)
      .groupMapReduce(r => Geo.regionFromCodePostal(r.codePostal))(_.effectif)(_ + _)
      .toList
      .map(RegionCount.apply)
      .sortBy(-_.nombrePros)

  def byDepartement(filter: Set[String]): List[DepartementCount] =
    keep(filter)
      .flatMap(r => Geo.departementFromCodePostal(r.codePostal).map(_ -> r.effectif))
      .groupMapReduce(_._1)(_._2)(_ + _)
      .toList
      .map((code, n) => DepartementCount(code, Geo.departementName(code), Geo.regionOf(code), n))
      .sortBy(-_.nombrePros)

  def topCommunes(filter: Set[String], limit: Int): List[CommuneCount] =
    keep(filter)
      .groupMapReduce(r => (r.commune, r.codePostal))(_.effectif)(_ + _)
      .toList
      .map { case ((commune, cp), n) => CommuneCount(commune, cp, n) }
      .sortBy(-_.nombrePros)
      .take(limit)

  def coverage(code: String): Option[CoverageReport] =
    val parProfession = rows
      .filter(r => Geo.departementFromCodePostal(r.codePostal).contains(code))
      .groupMapReduce(_.profession)(_.effectif)(_ + _)
      .toList
      .map(Profession.apply)
    Option.when(Geo.deptNames.contains(code))(ProfessionalSource.report(code, parProfession))

object InMemoryProfessionalSource:

  /** Échantillon factice couvrant l'Île-de-France, la province, la Corse et un DOM,
    * afin que les lots D et E travaillent sur des réponses réalistes avant le lot B.
    */
  val sample: List[Row] = List(
    Row("75001", "Paris", 48.8626, 2.3363, "Médecin", 412),
    Row("75001", "Paris", 48.8626, 2.3363, "Infirmier", 233),
    Row("75001", "Paris", 48.8626, 2.3363, "Pharmacien", 61),
    Row("69001", "Lyon", 45.7699, 4.8330, "Médecin", 188),
    Row("69001", "Lyon", 45.7699, 4.8330, "Infirmier", 121),
    Row("13001", "Marseille", 43.3011, 5.3800, "Médecin", 164),
    Row("13001", "Marseille", 43.3011, 5.3800, "Dentiste", 47),
    Row("33000", "Bordeaux", 44.8562, -0.5843, "Médecin", 131),
    Row("33000", "Bordeaux", 44.8562, -0.5843, "Kinésithérapeute", 88),
    Row("20000", "Ajaccio", 41.9264, 8.7369, "Médecin", 34),
    Row("20200", "Bastia", 42.6976, 9.4508, "Médecin", 27),
    Row("15000", "Aurillac", 44.9260, 2.4400, "Médecin", 19),
    Row("48000", "Mende", 44.5180, 3.4990, "Médecin", 11),
    Row("48000", "Mende", 44.5180, 3.4990, "Infirmier", 7),
    Row("79000", "Niort", 46.3239, -0.4644, "Médecin", 42),
    Row("97400", "Saint-Denis", -20.8789, 55.4481, "Médecin", 58)
  )

  /** Source factice utilisée par défaut au démarrage du serveur. */
  def stub: ProfessionalSource = new InMemoryProfessionalSource(sample)
