package fr.ipssi.healthmap.client.map

import fr.ipssi.healthmap.shared.model.*
import fr.ipssi.healthmap.shared.ref.Geo

/** Données simulées côté client, calquées sur `InMemoryProfessionalSource.sample`
  * du serveur.
  *
  * Le lot D travaille sur ces réponses simulées tant que le lot B n'a pas branché
  * DuckDB : la vitrine des cartes fonctionne alors sans dépendre des agrégats de
  * l'API (seuls les fonds GeoJSON restent servis par le serveur). Les agrégations
  * reproduisent celles du serveur, via le même référentiel `shared.ref.Geo`.
  */
object SampleData:

  final case class Row(
      codePostal: String,
      commune: String,
      latitude: Double,
      longitude: Double,
      profession: String,
      effectif: Int
  )

  val rows: List[Row] = List(
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

  /** Professions du référentiel simulé, triées par effectif décroissant. */
  val professions: List[Profession] =
    rows
      .groupMapReduce(_.profession)(_.effectif)(_ + _)
      .toList
      .map(Profession.apply)
      .sortBy(p => (-p.effectif, p.nom))

  private def keep(filter: Set[String]): List[Row] =
    if filter.isEmpty then rows else rows.filter(r => filter.contains(r.profession))

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
