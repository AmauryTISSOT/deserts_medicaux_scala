package fr.ipssi.healthmap.server.data

import java.nio.file.Path
import java.sql.DriverManager

class ProfessionalRepositorySuite extends munit.FunSuite:

  private val professionalsPath = Path.of("data", "fichier_professionnels_avec_coords.parquet")
  private val communesPath      = Path.of("data", "communes.parquet")

  private def dataAvailable: Boolean = professionalsPath.toFile.exists() && communesPath.toFile.exists()

  private lazy val repo =
    assume(dataAvailable, "Parquet(s) absent(s) : test ignoré")
    ProfessionalRepository.load(professionalsPath, communesPath)

  test("le parquet brut des professionnels compte 432 015 lignes") {
    assume(dataAvailable, "Parquet(s) absent(s) : test ignoré")
    Class.forName("org.duckdb.DuckDBDriver")
    val conn = DriverManager.getConnection("jdbc:duckdb:")
    try
      val stmt = conn.createStatement()
      val rs = stmt.executeQuery(
        s"SELECT COUNT(*) AS n FROM read_parquet('${professionalsPath.toString.replace('\\', '/')}')"
      )
      rs.next()
      assertEquals(rs.getInt("n"), 432015)
    finally conn.close()
  }

  test("le total servi (professions/total) correspond aux 429 998 professionnels résolus à une commune") {
    // Grain résolu hors non-résolus (`professionals_resolved WHERE
    // match_type != 'none'`), pas le grain géolocalisé de `load_data()` côté
    // Python : `professions`/`total` sont un référentiel métier (« combien de
    // tel professionnel exercent en France »), pas un sous-produit de la
    // disponibilité des coordonnées GPS. Voir la doc de tête de
    // ProfessionalRepository.
    assertEquals(repo.total(Set.empty), 429998)
    assertEquals(repo.professions.map(_.effectif).sum, 429998)
  }

  test("la liste des professions est triée par effectif décroissant") {
    val effectifs = repo.professions.map(_.effectif)
    assertEquals(effectifs, effectifs.sorted.reverse)
    assert(repo.professions.nonEmpty)
  }

  test("le filtre profession réduit le total et les agrégats") {
    val toutes = repo.total(Set.empty)
    val medecins = repo.total(Set("Médecin généraliste"))
    assert(medecins > 0 && medecins < toutes)

    val map = repo.mapPoints(Set("Médecin généraliste"))
    assert(map.forall(_.professions == List("Médecin généraliste")))
  }

  test("la Corse apparaît avec les codes 2A et 2B, rattachés à la région Corse") {
    val depts = repo.byDepartement(Set.empty).map(d => d.code -> d).toMap
    assert(depts.contains("2A"), "département 2A absent")
    assert(depts.contains("2B"), "département 2B absent")
    assertEquals(depts("2A").nom, "Corse-du-Sud")
    assertEquals(depts("2B").nom, "Haute-Corse")
    assertEquals(depts("2A").region, "Corse")
    assertEquals(depts("2B").region, "Corse")
    assert(depts("2A").nombrePros > 0)
    assert(depts("2B").nombrePros > 0)
  }

  test("le département 79 est libellé Deux-Sèvres (référentiel réel, pas de table figée)") {
    val depts = repo.byDepartement(Set.empty).map(d => d.code -> d.nom).toMap
    assertEquals(depts.get("79"), Some("Deux-Sèvres"))
  }

  test("la somme des populations par région égale le total national (67 648 309)") {
    val total = repo.densityByRegion(Set.empty).map(_.population).sum
    assertEquals(total, 67648309L)
  }

  test("le taux de repli de la jointure reste sous 7 %") {
    val stats = repo.joinStats
    assert(stats.fallbackRate < 7.0, s"taux de repli trop élevé : ${stats.fallbackRate}%")
    // `joinStats` porte sur le grain large (résolution indépendante du GPS,
    // jusqu'à 432 015), pas sur le grain géolocalisé (353 414) : voir
    // "deux totaux distincts" dans la doc de `ProfessionalRepository`.
    assertEquals(stats.total, 432015L)
  }

  test("l'effectif géolocalisé (carte) et l'effectif résolu (professions/région/département) sont deux totaux distincts, l'un incluant l'autre") {
    // Depuis la bascule de `agg_profession` sur le grain résolu, `total()`
    // porte lui-même l'effectif résolu (429 998) : le géolocalisé (353 414)
    // ne s'obtient plus par `total()` mais en sommant `mapPoints`, seule vue
    // restée sur `prof_grouped`.
    val geolocalise = repo.mapPoints(Set.empty).map(_.nombrePros.toLong).sum
    val resolu = repo.total(Set.empty).toLong
    assertEquals(geolocalise, 353414L)
    assertEquals(resolu, 429998L)
    assertEquals(resolu, repo.joinStats.exact + repo.joinStats.fallback)
    assert(resolu > geolocalise, "l'effectif résolu doit être strictement supérieur au géolocalisé")
  }

  test("professions, byRegion, byDepartement et total s'additionnent tous exactement au même grain résolu (429 998)") {
    // Verrouille la décision de la tâche 1 bis : agg_profession bascule sur
    // professionals_resolved (match_type != 'none'), le même grain que
    // byRegion/byDepartement — pas prof_grouped (géolocalisé, 353 414) ni
    // prof_all_grouped brut (inclurait les 2 017 non-résolus, 432 015). Un
    // effectif affiché à côté d'un libellé de profession doit être
    // vérifiable dans les vues région/département, et réciproquement.
    val total = repo.total(Set.empty).toLong
    assertEquals(total, 429998L)
    assertEquals(repo.professions.map(_.effectif.toLong).sum, total)
    assertEquals(repo.byRegion(Set.empty).map(_.nombrePros.toLong).sum, total)
    assertEquals(repo.byDepartement(Set.empty).map(_.nombrePros.toLong).sum, total)
  }

  test("Paris, Lyon et Marseille sont rattachés à leur département/région réels (arrondissementsPLM)") {
    val depts = repo.byDepartement(Set.empty).map(d => d.code -> d).toMap
    assert(depts("75").nombrePros > 0, "Paris (75) absent des agrégats départementaux")
    assert(depts("69").nombrePros > 0, "Lyon (69) absent des agrégats départementaux")
    assert(depts("13").nombrePros > 0, "Marseille (13) absent des agrégats départementaux")
    assertEquals(depts("75").region, "Île-de-France")
  }

  test("la couverture d'un département connu est calculable, celle d'un code inconnu vaut None") {
    assert(repo.coverage("75").isDefined)
    assertEquals(repo.coverage("999"), None)
  }

  test("la densité pour 100 000 habitants par grille INSEE couvre toute la population") {
    val stats = repo.densityByGrille(Set.empty)
    assert(stats.nonEmpty)
    assert(stats.forall(_.pour100k >= 0.0))
  }

  test("top-communes respecte la limite demandée") {
    val top = repo.topCommunes(Set.empty, 5)
    assertEquals(top.size, 5)
    assertEquals(top, top.sortBy(-_.nombrePros))
  }

  // Le rattachement Paris/Lyon/Marseille (`arrondissementsPLM`) est testé
  // ci-dessus, via `byDepartement` (grain résolu) : ils restent en revanche
  // absents de `topCommunes`/`mapPoints` (grain géolocalisé), faute de
  // coordonnées GPS dans le Parquet source (0 % des 33 769 concernés).

  test("le taux de non-résolution reste sous 1 %") {
    val stats = repo.joinStats
    assert(stats.unresolvedRate < 1.0, s"taux de non-résolution trop élevé : ${stats.unresolvedRate}%")
  }
