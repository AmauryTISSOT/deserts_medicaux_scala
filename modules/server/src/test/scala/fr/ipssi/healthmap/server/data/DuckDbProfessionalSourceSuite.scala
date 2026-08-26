package fr.ipssi.healthmap.server.data

import java.nio.file.Path

class DuckDbProfessionalSourceSuite extends munit.FunSuite:

  private val path = Path.of("data", "fichier_professionnels_avec_coords.parquet")

  private lazy val source =
    assume(path.toFile.exists(), s"Parquet absent ($path) : test ignoré")
    DuckDbProfessionalSource.load(path)

  test("le total correspond aux professionnels géolocalisés (432 015 moins les 78 601 sans coordonnées)") {
    assertEquals(source.total(Set.empty), 353414)
  }

  test("la Corse est éclatée en 2A et 2B, sans effectif nul") {
    val depts = source.byDepartement(Set.empty).map(d => d.code -> d).toMap
    assert(depts.contains("2A"), "département 2A absent des agrégats")
    assert(depts.contains("2B"), "département 2B absent des agrégats")
    assert(depts("2A").nombrePros > 0)
    assert(depts("2B").nombrePros > 0)
  }

  test("le département 79 est nommé Deux-Sèvres") {
    val depts = source.byDepartement(Set.empty).map(d => d.code -> d.nom).toMap
    assertEquals(depts.get("79"), Some("Deux-Sèvres"))
  }

  test("le filtre profession réduit l'effectif total") {
    val toutes = source.total(Set.empty)
    val medecins = source.total(Set("Médecin généraliste"))
    assert(medecins > 0 && medecins < toutes)
  }

  test("la couverture d'un département connu est calculable") {
    assert(source.coverage("75").isDefined)
    assert(source.coverage("00").isEmpty)
  }
