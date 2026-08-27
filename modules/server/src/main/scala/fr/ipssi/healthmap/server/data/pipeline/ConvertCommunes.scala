package fr.ipssi.healthmap.server.data.pipeline

import java.sql.DriverManager

/** Conversion ponctuelle du référentiel communes (JSON, 62 Mo) en
  * `data/communes.parquet` (quelques Mo, versionnable dans Git).
  *
  * Exécution unique, à la demande : `sbt "server/runMain fr.ipssi.healthmap.server.data.pipeline.ConvertCommunes"`.
  * N'est pas rejoué au démarrage du serveur — seul `data/communes.parquet`
  * est lu par `ProfessionalRepository`.
  *
  * Le fichier source `data/raw/communes-france-avec-polygon-2025.json` est
  * une source brute ignorée par Git (voir `.gitignore`) ; le `polygone`
  * (10 Mo sur 62) et les colonnes inutiles à l'agrégation sont exclus du
  * résultat.
  */
object ConvertCommunes:

  private val rawPath   = "data/raw/communes-france-avec-polygon-2025.json"
  private val parquetOut = "data/communes.parquet"

  def main(args: Array[String]): Unit =
    Class.forName("org.duckdb.DuckDBDriver")
    val conn = DriverManager.getConnection("jdbc:duckdb:")
    val stmt = conn.createStatement()

    // Le fichier entier est un unique objet JSON (~62 Mo) : la limite par
    // défaut de DuckDB pour un objet (16 Mo) doit être relevée.
    stmt.execute(
      s"""CREATE OR REPLACE TABLE communes AS
         |SELECT
         |  r.code_insee            AS code_insee,
         |  r.nom_standard           AS nom_standard,
         |  r.nom_sans_accent        AS nom_sans_accent,
         |  r.code_postal            AS code_postal,
         |  r.codes_postaux          AS codes_postaux,
         |  r.dep_code               AS dep_code,
         |  r.dep_nom                AS dep_nom,
         |  r.reg_code               AS reg_code,
         |  r.reg_nom                AS reg_nom,
         |  r.population             AS population,
         |  r.superficie_km2         AS superficie_km2,
         |  r.densite                AS densite,
         |  r.grille_densite_texte   AS grille_densite_texte,
         |  r.latitude_mairie        AS latitude_mairie,
         |  r.longitude_mairie       AS longitude_mairie
         |FROM (
         |  SELECT unnest(data) AS r
         |  FROM read_json_auto('$rawPath', maximum_object_size=200000000)
         |)""".stripMargin
    )

    // Diagnostics de sanité avant l'écriture du Parquet.
    val rs1 = stmt.executeQuery("SELECT COUNT(*) FROM communes")
    rs1.next()
    println(s"communes : ${rs1.getInt(1)} lignes (attendu 34 935)")

    val rs2 = stmt.executeQuery(
      "SELECT COUNT(DISTINCT code_postal) FROM communes"
    )
    rs2.next()
    println(s"codes postaux distincts : ${rs2.getInt(1)} (attendu 6 012)")

    val rs3 = stmt.executeQuery(
      "SELECT dep_code, COUNT(*) FROM communes WHERE dep_code IN ('2A','2B') GROUP BY dep_code ORDER BY dep_code"
    )
    while rs3.next() do println(s"Corse ${rs3.getString(1)} : ${rs3.getInt(2)} communes")

    val rs4 = stmt.executeQuery("SELECT SUM(population), COUNT(*) FILTER (WHERE population IS NULL) FROM communes")
    rs4.next()
    println(s"population totale : ${rs4.getLong(1)} (attendu 67 648 309), communes sans population : ${rs4.getInt(2)} (attendu 9)")

    stmt.execute(s"COPY communes TO '$parquetOut' (FORMAT PARQUET)")
    println(s"écrit : $parquetOut")

    conn.close()
