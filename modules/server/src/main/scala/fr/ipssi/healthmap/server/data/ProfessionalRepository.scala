package fr.ipssi.healthmap.server.data

import fr.ipssi.healthmap.shared.model.*

import java.nio.file.Path
import java.sql.{Connection, DriverManager, ResultSet}

/** Implémentation DuckDB de `ProfessionalSource` (lot B).
  *
  * Contrairement à une dérivation du département depuis le code postal, le
  * département et la région de chaque professionnel sont ceux du référentiel
  * `data/communes.parquet` (INSEE), obtenus par jointure sur
  * `(code_postal, nom de commune normalisé)`, avec repli sur `code_postal`
  * seul quand le nom ne correspond pas exactement (voir `Normalization`).
  * Aucune table de correspondance en dur, aucun `CASE WHEN`/`substr` sur le
  * code postal.
  *
  * Deux grains, deux totaux — à ne pas confondre côté client (lot E) :
  *   - `prof_grouped` (353 414 professionnels **géolocalisés**, GPS non nul,
  *     comme `load_data()` côté Python) : source de `mapPoints` et
  *     `topCommunes`, qui ont besoin d'un point GPS.
  *   - `professionals_resolved` filtré sur les correspondances effectives
  *     (`match_type != 'none'`, 429 998 professionnels rattachés à une
  *     commune réelle, GPS ou non) : source de `professions`, `total`,
  *     `byRegion`, `byDepartement`, `coverage`, `densityBy*`, qui n'ont besoin
  *     que de la commune résolue, pas d'un point GPS. Mesuré : 97,4 % des
  *     78 601 professionnels sans GPS se résolvent tout de même à une
  *     commune ; les exclure de ces agrégats aurait été une perte sèche sans
  *     rapport avec la qualité de la jointure. Les 2 017 professionnels non
  *     résolus (`match_type = 'none'`, 0,47 %) n'appartiennent en revanche à
  *     aucun territoire connu : les compter dans ce référentiel reviendrait à
  *     affirmer qu'ils exercent quelque part, alors que la jointure conclut
  *     l'inverse. Leur place est dans `joinStats`, qui mesure la qualité de
  *     la jointure, pas dans la donnée métier.
  *
  *   `professions`/`total`/`byRegion`/`byDepartement`/`coverage`/`densityBy*`
  *   sortent donc tous du même grain et s'additionnent exactement (429 998).
  *   `mapPoints`/`topCommunes` restent sur le grain géolocalisé et
  *   afficheront des totaux inférieurs (353 414) — c'est attendu, pas un bug.
  *
  * Tous les agrégats (`agg_*`) sont calculés une seule fois à la
  * construction. Les méthodes exposées ne font ensuite que filtrer/
  * reregrouper ces tables déjà réduites (de quelques dizaines à quelques
  * milliers de lignes) — jamais les lignes sources : c'est le sens donné ici
  * à « mémoïsation », une pré-agrégation combinatoire complète pour chaque
  * sous-ensemble de professions possible n'étant pas praticable.
  *
  * Paris, Lyon et Marseille n'ont qu'une ligne chacune dans le référentiel
  * communes-france, avec un code postal générique fictif et sans leurs
  * arrondissements réels. Leurs codes postaux d'arrondissement sont ajoutés
  * explicitement à l'espace de recherche (voir `arrondissementsPLM`),
  * rattachés à leur `code_insee` réel : le département/région/population
  * qu'ils portent restent ceux du référentiel, jamais dérivés du code postal.
  * Ils restent en revanche absents de `mapPoints`/`topCommunes` : les 33 769
  * professionnels concernés n'ont aucune coordonnée GPS dans le Parquet
  * source (0 %, vérifié), un problème distinct du rattachement communes.
  */
final class ProfessionalRepository private (conn: Connection) extends ProfessionalSource:

  // DuckDB JDBC n'accepte pas l'exécution concurrente de requêtes sur une même
  // connexion (« Attempting to execute an unsuccessful or closed pending query
  // result »). Or http4s traite les requêtes en parallèle et le client lot E en
  // déclenche quatre d'un coup (carte, régions, départements, top communes) : on
  // sérialise donc les accès. Les requêtes portent sur des tables pré-agrégées
  // de quelques milliers de lignes au plus, le coût de la sérialisation est
  // négligeable. (Alternative si le débit l'exigeait : une connexion dupliquée
  // par requête via `DuckDBConnection.duplicate()`.)
  private val lock = new AnyRef

  private def query[A](sql: String)(extract: ResultSet => A): List[A] =
    lock.synchronized {
      val stmt = conn.createStatement()
      try
        val rs = stmt.executeQuery(sql)
        val buf = collection.mutable.ListBuffer.empty[A]
        while rs.next() do buf += extract(rs)
        buf.toList
      finally stmt.close()
    }

  private def queryOne[A](sql: String)(extract: ResultSet => A): Option[A] =
    query(sql)(extract).headOption

  private def sqlEscape(s: String): String = s.replace("'", "''")

  private def professionFilter(filter: Set[String]): String =
    if filter.isEmpty then "TRUE"
    else filter.map(p => s"'${sqlEscape(p)}'").mkString("profession IN (", ", ", ")")

  // --- ProfessionalSource -----------------------------------------------

  val professions: List[Profession] =
    query("SELECT profession, effectif FROM agg_profession ORDER BY effectif DESC, profession ASC") { rs =>
      Profession(rs.getString("profession"), rs.getInt("effectif"))
    }

  def total(filter: Set[String]): Int =
    queryOne(s"SELECT COALESCE(SUM(effectif), 0) AS n FROM agg_profession WHERE ${professionFilter(filter)}")(_.getInt("n"))
      .getOrElse(0)

  def mapPoints(filter: Set[String]): List[MapPoint] =
    val sql =
      s"""SELECT code_postal, commune, latitude, longitude,
         |       SUM(effectif) AS n,
         |       string_agg(DISTINCT profession, '|' ORDER BY profession) AS professions
         |FROM agg_map
         |WHERE ${professionFilter(filter)}
         |GROUP BY code_postal, commune, latitude, longitude""".stripMargin
    query(sql) { rs =>
      MapPoint(
        codePostal = rs.getString("code_postal"),
        commune = rs.getString("commune"),
        latitude = rs.getDouble("latitude"),
        longitude = rs.getDouble("longitude"),
        nombrePros = rs.getInt("n"),
        professions = rs.getString("professions").split('|').toList
      )
    }

  def byRegion(filter: Set[String]): List[RegionCount] =
    val sql =
      s"""SELECT reg_nom, SUM(effectif) AS n
         |FROM agg_region
         |WHERE ${professionFilter(filter)}
         |GROUP BY reg_nom
         |ORDER BY n DESC""".stripMargin
    query(sql)(rs => RegionCount(rs.getString("reg_nom"), rs.getInt("n")))

  def byDepartement(filter: Set[String]): List[DepartementCount] =
    val sql =
      s"""SELECT dep_code, dep_nom, reg_nom, SUM(effectif) AS n
         |FROM agg_departement
         |WHERE ${professionFilter(filter)}
         |GROUP BY dep_code, dep_nom, reg_nom
         |ORDER BY n DESC""".stripMargin
    query(sql)(rs => DepartementCount(rs.getString("dep_code"), rs.getString("dep_nom"), rs.getString("reg_nom"), rs.getInt("n")))

  def topCommunes(filter: Set[String], limit: Int): List[CommuneCount] =
    val sql =
      s"""SELECT commune, code_postal, SUM(effectif) AS n
         |FROM agg_commune
         |WHERE ${professionFilter(filter)}
         |GROUP BY commune, code_postal
         |ORDER BY n DESC
         |LIMIT $limit""".stripMargin
    query(sql)(rs => CommuneCount(rs.getString("commune"), rs.getString("code_postal"), rs.getInt("n")))

  def coverage(code: String): Option[CoverageReport] =
    val deptRow = queryOne(
      s"SELECT dep_nom, reg_nom FROM communes_dept WHERE dep_code = '${sqlEscape(code)}'"
    )(rs => (rs.getString("dep_nom"), rs.getString("reg_nom")))

    deptRow.map { case (depNom, regNom) =>
      val parProfession = query(
        s"""SELECT profession, SUM(effectif) AS n
           |FROM agg_departement
           |WHERE dep_code = '${sqlEscape(code)}'
           |GROUP BY profession
           |ORDER BY n DESC""".stripMargin
      )(rs => Profession(rs.getString("profession"), rs.getInt("n")))

      val total = parProfession.map(_.effectif).sum
      CoverageReport(
        code = code,
        nom = depNom,
        region = regNom,
        total = total,
        parProfession = parProfession,
        niveau = ProfessionalSource.niveau(total)
      )
    }

  // --- Bonus lot B : densité et couverture par spécialité -----------------
  // Ces méthodes ne font pas partie du contrat `ProfessionalSource` (lot C) ;
  // elles sont disponibles pour de futures routes/modèles, à négocier avec
  // le lot C le cas échéant.

  /** Couverture d'un département pour une seule spécialité. */
  def coverageForSpecialty(code: String, profession: String): Option[CoverageReport] =
    coverage(code).map { report =>
      val n = report.parProfession.find(_.nom == profession).map(_.effectif).getOrElse(0)
      report.copy(total = n, parProfession = List(Profession(profession, n)), niveau = ProfessionalSource.niveau(n))
    }

  /** Densité pour 100 000 habitants, par commune (référentiel INSEE complet :
    * une commune sans professionnel résolu apparaît avec un effectif de 0).
    */
  def densityByCommune(filter: Set[String]): List[DensityStat] =
    val sql =
      s"""SELECT c.code_insee AS code, c.nom_standard AS nom, c.population AS population,
         |       COALESCE(e.effectif, 0) AS effectif
         |FROM communes_ref c
         |LEFT JOIN (
         |  SELECT code_insee, SUM(effectif) AS effectif
         |  FROM agg_effectif_commune
         |  WHERE ${professionFilter(filter)}
         |  GROUP BY code_insee
         |) e ON e.code_insee = c.code_insee
         |WHERE c.population > 0""".stripMargin
    query(sql)(rs => densityStat(rs.getString("code"), rs.getString("nom"), rs.getInt("effectif"), rs.getLong("population")))

  /** Densité pour 100 000 habitants, par département. */
  override def densityByDepartement(filter: Set[String]): List[DensityStat] =
    val sql =
      s"""SELECT d.dep_code AS code, d.dep_nom AS nom, d.population AS population,
         |       COALESCE(e.effectif, 0) AS effectif
         |FROM dept_population d
         |LEFT JOIN (
         |  SELECT dep_code, SUM(effectif) AS effectif
         |  FROM agg_departement
         |  WHERE ${professionFilter(filter)}
         |  GROUP BY dep_code
         |) e ON e.dep_code = d.dep_code
         |WHERE d.population > 0""".stripMargin
    query(sql)(rs => densityStat(rs.getString("code"), rs.getString("nom"), rs.getInt("effectif"), rs.getLong("population")))

  /** Densité pour 100 000 habitants, par région. */
  override def densityByRegion(filter: Set[String]): List[DensityStat] =
    val sql =
      s"""SELECT r.reg_nom AS nom, r.population AS population,
         |       COALESCE(e.effectif, 0) AS effectif
         |FROM region_population r
         |LEFT JOIN (
         |  SELECT reg_nom, SUM(effectif) AS effectif
         |  FROM agg_region
         |  WHERE ${professionFilter(filter)}
         |  GROUP BY reg_nom
         |) e ON e.reg_nom = r.reg_nom
         |WHERE r.population > 0""".stripMargin
    query(sql)(rs => densityStat(rs.getString("nom"), rs.getString("nom"), rs.getInt("effectif"), rs.getLong("population")))

  /** Croisement avec `grille_densite_texte` : professionnels pour 100 000
    * habitants par type de territoire INSEE (grands centres urbains, bourgs
    * ruraux, etc.) — le cœur de la mesure de désert médical.
    */
  def densityByGrille(filter: Set[String]): List[GrilleDensityStat] =
    val sql =
      s"""SELECT g.grille_densite_texte AS grille, g.population AS population,
         |       COALESCE(e.effectif, 0) AS effectif
         |FROM grille_population g
         |LEFT JOIN (
         |  SELECT grille_densite_texte, SUM(effectif) AS effectif
         |  FROM agg_effectif_grille
         |  WHERE ${professionFilter(filter)}
         |  GROUP BY grille_densite_texte
         |) e ON e.grille_densite_texte = g.grille_densite_texte
         |WHERE g.population > 0
         |ORDER BY g.grille_densite_texte""".stripMargin
    query(sql) { rs =>
      val population = rs.getLong("population")
      val effectif = rs.getInt("effectif")
      GrilleDensityStat(rs.getString("grille"), effectif, population, pour100k(effectif, population))
    }

  private def densityStat(code: String, nom: String, effectif: Int, population: Long): DensityStat =
    DensityStat(code, nom, effectif, population, pour100k(effectif, population))

  private def pour100k(effectif: Int, population: Long): Double =
    if population <= 0 then 0.0 else math.round(effectif * 100000.0 * 10 / population) / 10.0

  /** Qualité de la jointure professionnels ↔ communes — le chiffre à citer.
    * Porte sur le grain large (432 015, GPS ou non ; voir la doc de tête).
    */
  val joinStats: JoinStats =
    queryOne(
      """SELECT
        |  SUM(effectif) AS total,
        |  SUM(effectif) FILTER (WHERE match_type = 'exact') AS exact_n,
        |  SUM(effectif) FILTER (WHERE match_type = 'fallback') AS fallback_n,
        |  SUM(effectif) FILTER (WHERE match_type = 'none') AS unresolved_n
        |FROM professionals_resolved""".stripMargin
    ) { rs =>
      JoinStats(rs.getLong("total"), rs.getLong("exact_n"), rs.getLong("fallback_n"), rs.getLong("unresolved_n"))
    }.getOrElse(JoinStats(0, 0, 0, 0))

  /** Même mesure que `joinStats`, restreinte aux 353 414 professionnels
    * géolocalisés (grain de la carte et du classement des communes) : la
    * qualité de la jointure y est mesurée séparément, car ce sous-ensemble
    * n'a pas nécessairement le même profil de correspondance que l'ensemble
    * du grain large. Vérifié indépendamment (recomptage sur les lignes
    * brutes de `professionals`, hors `prof_grouped`) : les 2 017
    * professionnels non résolus du grain large sont tous parmi les 78 601
    * sans GPS, aucun parmi les 353 414 géolocalisés.
    */
  val joinStatsGeolocalise: JoinStats =
    queryOne(
      """SELECT
        |  SUM(pg.effectif) AS total,
        |  SUM(pg.effectif) FILTER (WHERE r.match_type = 'exact') AS exact_n,
        |  SUM(pg.effectif) FILTER (WHERE r.match_type = 'fallback') AS fallback_n,
        |  SUM(pg.effectif) FILTER (WHERE r.match_type = 'none') AS unresolved_n
        |FROM prof_grouped pg
        |JOIN resolution r ON r.code_postal = pg.code_postal AND r.commune = pg.commune""".stripMargin
    ) { rs =>
      JoinStats(rs.getLong("total"), rs.getLong("exact_n"), rs.getLong("fallback_n"), rs.getLong("unresolved_n"))
    }.getOrElse(JoinStats(0, 0, 0, 0))

  private[data] def close(): Unit = conn.close()

object ProfessionalRepository:

  private def escapePath(path: Path): String = path.toString.replace("'", "''").replace('\\', '/')

  /** Codes postaux d'arrondissement de Paris, Lyon et Marseille, absents du
    * référentiel communes-france : celui-ci ne contient qu'une ligne par
    * ville, avec un code postal générique (`75000`/`69000`/`13000`) qui
    * n'apparaît jamais dans les données CNAM, et `codes_postaux` à `NULL`.
    * Sans cette liste, la jointure exacte échoue *et* le repli sur code
    * postal seul échoue aussi : les trois plus grandes villes de France
    * disparaissent des choroplèthes et du classement des communes.
    *
    * Ceci n'est PAS une dérivation « code postal -> département » : c'est un
    * complément explicite et vérifiable de l'espace de recherche des codes
    * postaux (comme `codes_postaux` le fait déjà pour Bordeaux ou toute
    * commune à plusieurs codes). Le `code_insee` reste la seule clé vers le
    * référentiel ; département, région, population et typologie continuent
    * d'en être issus par jointure, jamais calculés à partir du code postal.
    */
  private val arrondissementsPLM: List[(String, String)] =
    ((1 to 20).map(n => f"${75000 + n}%05d" -> "75056")   // Paris   (75056)
      ++ (1 to 9).map(n => f"${69000 + n}%05d" -> "69123")  // Lyon     (69123)
      ++ (1 to 16).map(n => f"${13000 + n}%05d" -> "13055") // Marseille(13055)
    ).toList

  /** Ouvre une connexion DuckDB en mémoire, charge les deux Parquet et
    * matérialise tous les agrégats une seule fois.
    */
  def load(professionalsParquet: Path, communesParquet: Path): ProfessionalRepository =
    Class.forName("org.duckdb.DuckDBDriver")
    val conn = DriverManager.getConnection("jdbc:duckdb:")
    val stmt = conn.createStatement()
    def exec(sql: String): Unit = stmt.execute(sql)

    val profPath = escapePath(professionalsParquet)
    val commPath = escapePath(communesParquet)
    val normCommune = Normalization.sql("commune")
    val normNomStandard = Normalization.sql("nom_standard")

    exec(s"CREATE TABLE professionals AS SELECT * FROM read_parquet('$profPath')")
    exec(s"CREATE TABLE communes AS SELECT * FROM read_parquet('$commPath')")

    // Grain étroit : professionnels GÉOLOCALISÉS (coordonnées GPS non
    // nulles), comme le faisait `load_data()` côté Python. Sert uniquement à
    // la carte et au classement des communes, qui ont besoin d'un point GPS
    // par professionnel.
    exec(
      s"""CREATE TABLE prof_grouped AS
         |SELECT code_postal, commune, $normCommune AS commune_norm,
         |       COALESCE(profession, 'Profession inconnue') AS profession,
         |       latitude, longitude, COUNT(*) AS effectif
         |FROM professionals
         |WHERE code_postal IS NOT NULL AND commune IS NOT NULL
         |  AND latitude IS NOT NULL AND longitude IS NOT NULL
         |GROUP BY code_postal, commune, commune_norm,
         |         COALESCE(profession, 'Profession inconnue'), latitude, longitude""".stripMargin
    )

    // Grain large : TOUS les professionnels ayant un code postal et une
    // commune, GPS ou non. Mesuré : 97,4 % des 78 601 professionnels sans GPS
    // se résolvent tout de même à une commune via `(code_postal, nom)` ; les
    // exclure du décompte régional/départemental serait une perte sèche sans
    // rapport avec la qualité de la jointure. Sert à région, département,
    // couverture et densité : aucun de ces agrégats n'a besoin d'un point GPS
    // par professionnel, seulement de la commune résolue.
    //
    // Deux totaux distincts en découlent, à ne pas confondre dans l'IHM
    // (lot E) : l'effectif géolocalisé (`mapPoints`/`topCommunes`, 353 414)
    // et l'effectif résolu hors non-résolus (`professions`/`total`/région/
    // département/couverture/densité, 429 998 — voir `professionals_resolved`
    // plus bas).
    exec(
      s"""CREATE TABLE prof_all_grouped AS
         |SELECT code_postal, commune, $normCommune AS commune_norm,
         |       COALESCE(profession, 'Profession inconnue') AS profession,
         |       COUNT(*) AS effectif
         |FROM professionals
         |WHERE code_postal IS NOT NULL AND commune IS NOT NULL
         |GROUP BY code_postal, commune, commune_norm,
         |         COALESCE(profession, 'Profession inconnue')""".stripMargin
    )

    exec("CREATE TABLE agg_map AS SELECT code_postal, commune, latitude, longitude, profession, effectif FROM prof_grouped")
    exec(
      """CREATE TABLE agg_commune AS
        |SELECT code_postal, commune, profession, SUM(effectif) AS effectif
        |FROM prof_grouped
        |GROUP BY code_postal, commune, profession""".stripMargin
    )

    // Complément d'espace de recherche pour Paris/Lyon/Marseille (voir
    // `arrondissementsPLM`) : une table (code postal, code_insee) rejointe à
    // `communes` juste après, exactement comme `codes_postaux` l'est déjà.
    val plmValues = arrondissementsPLM.map { case (pc, insee) => s"('$pc', '$insee')" }.mkString(", ")
    exec(s"CREATE TABLE arrondissements_plm AS SELECT * FROM (VALUES $plmValues) AS t(pc, code_insee)")

    // `codes_postaux` est une chaîne CSV (pas une liste typée) qui recense,
    // pour les communes qui en ont plusieurs (ex. Bordeaux : 82 codes), tous
    // leurs codes postaux valides : `code_postal` seul ne suffit pas comme
    // espace de recherche, il faut l'éclater et l'unir à `code_postal`.
    exec(
      s"""CREATE TABLE communes_expanded AS
         |SELECT code_insee, $normNomStandard AS nom_norm,
         |       dep_code, dep_nom, reg_code, reg_nom, population, grille_densite_texte,
         |       code_postal AS pc
         |FROM communes WHERE code_postal IS NOT NULL
         |UNION ALL
         |SELECT code_insee, $normNomStandard AS nom_norm,
         |       dep_code, dep_nom, reg_code, reg_nom, population, grille_densite_texte,
         |       trim(unnest(str_split(codes_postaux, ','))) AS pc
         |FROM communes WHERE codes_postaux IS NOT NULL
         |UNION ALL
         |SELECT c.code_insee, $normNomStandard AS nom_norm,
         |       c.dep_code, c.dep_nom, c.reg_code, c.reg_nom, c.population, c.grille_densite_texte,
         |       a.pc AS pc
         |FROM arrondissements_plm a
         |JOIN communes c ON c.code_insee = a.code_insee""".stripMargin
    )

    // Résolution exacte : (code postal, nom normalisé). En cas de doublon
    // (rarissime), on retient la commune la plus peuplée.
    exec(
      """CREATE TABLE communes_keys AS
        |SELECT pc, nom_norm,
        |  arg_max(code_insee, population) AS code_insee,
        |  arg_max(dep_code, population) AS dep_code,
        |  arg_max(dep_nom, population) AS dep_nom,
        |  arg_max(reg_code, population) AS reg_code,
        |  arg_max(reg_nom, population) AS reg_nom,
        |  max(population) AS population,
        |  arg_max(grille_densite_texte, population) AS grille_densite_texte
        |FROM communes_expanded
        |GROUP BY pc, nom_norm""".stripMargin
    )

    // Repli : code postal seul. Quand plusieurs communes le partagent
    // (jusqu'à 46 dans ce référentiel), on retient la plus peuplée : c'est la
    // candidate la plus probable en l'absence de correspondance de nom.
    exec(
      """CREATE TABLE communes_by_cp AS
        |SELECT pc,
        |  arg_max(code_insee, population) AS code_insee,
        |  arg_max(dep_code, population) AS dep_code,
        |  arg_max(dep_nom, population) AS dep_nom,
        |  arg_max(reg_code, population) AS reg_code,
        |  arg_max(reg_nom, population) AS reg_nom,
        |  max(population) AS population,
        |  arg_max(grille_densite_texte, population) AS grille_densite_texte
        |FROM communes_expanded
        |GROUP BY pc""".stripMargin
    )

    exec(
      """CREATE TABLE resolution AS
        |SELECT
        |  pp.code_postal, pp.commune,
        |  COALESCE(ek.code_insee, fk.code_insee) AS code_insee,
        |  COALESCE(ek.dep_code, fk.dep_code) AS dep_code,
        |  COALESCE(ek.dep_nom, fk.dep_nom) AS dep_nom,
        |  COALESCE(ek.reg_code, fk.reg_code) AS reg_code,
        |  COALESCE(ek.reg_nom, fk.reg_nom) AS reg_nom,
        |  COALESCE(ek.population, fk.population) AS population,
        |  COALESCE(ek.grille_densite_texte, fk.grille_densite_texte) AS grille_densite_texte,
        |  CASE WHEN ek.code_insee IS NOT NULL THEN 'exact'
        |       WHEN fk.code_insee IS NOT NULL THEN 'fallback'
        |       ELSE 'none' END AS match_type
        |FROM (SELECT DISTINCT code_postal, commune, commune_norm FROM prof_all_grouped) pp
        |LEFT JOIN communes_keys ek ON ek.pc = pp.code_postal AND ek.nom_norm = pp.commune_norm
        |LEFT JOIN communes_by_cp fk ON fk.pc = pp.code_postal""".stripMargin
    )

    // Chaque professionnel résolu (GPS ou non), enrichi du département/
    // région/population/typologie réels issus du référentiel (jamais dérivés
    // du seul code postal).
    exec(
      """CREATE TABLE professionals_resolved AS
        |SELECT pg.*, r.code_insee, r.dep_code, r.dep_nom, r.reg_code, r.reg_nom,
        |       r.population AS commune_population, r.grille_densite_texte, r.match_type
        |FROM prof_all_grouped pg
        |JOIN resolution r ON r.code_postal = pg.code_postal AND r.commune = pg.commune""".stripMargin
    )

    // Référentiel des professions : grain résolu hors non-résolus, comme
    // agg_region/agg_departement ci-dessous — pas prof_grouped (grain
    // géolocalisé, réservé à la carte) ni prof_all_grouped brut (inclurait
    // les 2 017 professionnels sans territoire connu, voir doc de tête).
    exec(
      """CREATE TABLE agg_profession AS
        |SELECT profession, SUM(effectif) AS effectif
        |FROM professionals_resolved
        |WHERE match_type != 'none'
        |GROUP BY profession""".stripMargin
    )

    exec(
      """CREATE TABLE agg_region AS
        |SELECT reg_nom, profession, SUM(effectif) AS effectif
        |FROM professionals_resolved
        |WHERE match_type != 'none'
        |GROUP BY reg_nom, profession""".stripMargin
    )
    exec(
      """CREATE TABLE agg_departement AS
        |SELECT dep_code, dep_nom, reg_nom, profession, SUM(effectif) AS effectif
        |FROM professionals_resolved
        |WHERE match_type != 'none'
        |GROUP BY dep_code, dep_nom, reg_nom, profession""".stripMargin
    )
    exec(
      """CREATE TABLE agg_effectif_commune AS
        |SELECT code_insee, profession, SUM(effectif) AS effectif
        |FROM professionals_resolved
        |WHERE match_type != 'none'
        |GROUP BY code_insee, profession""".stripMargin
    )
    exec(
      """CREATE TABLE agg_effectif_grille AS
        |SELECT grille_densite_texte, profession, SUM(effectif) AS effectif
        |FROM professionals_resolved
        |WHERE match_type != 'none' AND grille_densite_texte IS NOT NULL
        |GROUP BY grille_densite_texte, profession""".stripMargin
    )

    // Références de population, indépendantes de la résolution des
    // professionnels (une commune sans professionnel résolu doit apparaître
    // avec un effectif de 0, pas disparaître des agrégats de densité).
    exec("CREATE TABLE communes_ref AS SELECT code_insee, nom_standard, population FROM communes WHERE code_insee IS NOT NULL")
    exec(
      """CREATE TABLE communes_dept AS
        |SELECT DISTINCT dep_code, dep_nom, reg_nom FROM communes WHERE dep_code IS NOT NULL""".stripMargin
    )
    exec(
      """CREATE TABLE dept_population AS
        |SELECT dep_code, dep_nom, SUM(population) AS population
        |FROM communes WHERE dep_code IS NOT NULL
        |GROUP BY dep_code, dep_nom""".stripMargin
    )
    exec(
      """CREATE TABLE region_population AS
        |SELECT reg_nom, SUM(population) AS population
        |FROM communes WHERE reg_nom IS NOT NULL
        |GROUP BY reg_nom""".stripMargin
    )
    exec(
      """CREATE TABLE grille_population AS
        |SELECT grille_densite_texte, SUM(population) AS population
        |FROM communes WHERE grille_densite_texte IS NOT NULL
        |GROUP BY grille_densite_texte""".stripMargin
    )

    stmt.close()
    new ProfessionalRepository(conn)
