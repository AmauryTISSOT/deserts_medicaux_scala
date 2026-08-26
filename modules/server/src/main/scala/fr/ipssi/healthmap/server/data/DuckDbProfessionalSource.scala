package fr.ipssi.healthmap.server.data

import java.nio.file.Path
import java.sql.DriverManager

/** Implémentation DuckDB de `ProfessionalSource` (lot B).
  *
  * DuckDB lit le Parquet et fait le premier regroupement (par code postal,
  * commune, coordonnées et profession) directement en SQL — plus rapide que
  * de itérer les 432 015 lignes une à une en Scala. Le résultat, une liste de
  * `Row` très inférieure en taille, est ensuite délégué à
  * `InMemoryProfessionalSource` : tous les agrégats par région, département,
  * top communes et couverture restent ainsi la même logique Scala déjà
  * utilisée — et testée — par la source factice, conformément au README
  * (« le lot B la remplace [...] sans toucher aux routes »).
  *
  * Seules les lignes sans coordonnées GPS sont écartées (78 601 sur 432 015,
  * comme le faisait déjà `load_data()` côté Python avec son
  * `dropna(subset=["latitude", "longitude"])`) ; une profession manquante est
  * conservée sous le libellé « Profession inconnue » plutôt que d'être
  * supprimée, pour ne pas fausser les effectifs par département ou région.
  *
  * Comme le reste de `ProfessionalSource`, le chargement est synchrone et
  * n'a lieu qu'une fois, au démarrage du serveur.
  */
object DuckDbProfessionalSource:

  def load(parquetPath: Path): ProfessionalSource =
    new InMemoryProfessionalSource(readRows(parquetPath))

  private def readRows(parquetPath: Path): List[Row] =
    Class.forName("org.duckdb.DuckDBDriver")
    val conn = DriverManager.getConnection("jdbc:duckdb:")
    try
      val stmt = conn.createStatement()
      try
        val sql =
          s"""SELECT code_postal, commune, latitude, longitude,
             |       COALESCE(profession, 'Profession inconnue') AS profession,
             |       COUNT(*) AS effectif
             |FROM read_parquet('${escape(parquetPath)}')
             |WHERE code_postal IS NOT NULL AND commune IS NOT NULL
             |  AND latitude IS NOT NULL AND longitude IS NOT NULL
             |GROUP BY code_postal, commune, latitude, longitude, COALESCE(profession, 'Profession inconnue')""".stripMargin
        val rs = stmt.executeQuery(sql)
        val buf = collection.mutable.ListBuffer.empty[Row]
        while rs.next() do
          buf += Row(
            codePostal = rs.getString("code_postal"),
            commune = rs.getString("commune"),
            latitude = rs.getDouble("latitude"),
            longitude = rs.getDouble("longitude"),
            profession = rs.getString("profession"),
            effectif = rs.getInt("effectif")
          )
        buf.toList
      finally stmt.close()
    finally conn.close()

  private def escape(path: Path): String =
    path.toString.replace("'", "''").replace('\\', '/')
