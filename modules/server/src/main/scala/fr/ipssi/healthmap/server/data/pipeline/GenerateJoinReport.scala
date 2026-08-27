package fr.ipssi.healthmap.server.data.pipeline

import fr.ipssi.healthmap.server.data.{JoinStats, ProfessionalRepository}

import java.nio.file.{Files, Path}
import java.time.LocalDate

/** Écrit `STATISTIQUES_JOINTURE.md` à la racine du dépôt : les chiffres de
  * `JoinStats`, affichés au démarrage du serveur puis perdus, dans un
  * fichier que le lot E peut lire pour la page « À propos » et que l'équipe
  * peut citer dans un rapport.
  *
  * Exécution : `sbt "server/runMain fr.ipssi.healthmap.server.data.pipeline.GenerateJoinReport"`.
  * À rejouer après toute mise à jour des Parquet sources.
  */
object GenerateJoinReport:

  private val professionalsPath = Path.of("data", "fichier_professionnels_avec_coords.parquet")
  private val communesPath      = Path.of("data", "communes.parquet")
  private val outPath           = Path.of("STATISTIQUES_JOINTURE.md")

  private def formatN(n: Long): String = f"$n%,d".replace(",", " ")

  private def ligne(nom: String, n: Long, rate: Double): String =
    f"| $nom | ${formatN(n)} | $rate%.1f %% |"

  private def table(stats: JoinStats): String =
    List(
      "| | Valeur | % |",
      "| --- | ---: | ---: |",
      ligne("Total", stats.total, 100.0),
      ligne("Correspondance exacte", stats.exact, stats.exactRate),
      ligne("Repli (code postal seul)", stats.fallback, stats.fallbackRate),
      ligne("Non résolu", stats.unresolved, stats.unresolvedRate)
    ).mkString("\n")

  def main(args: Array[String]): Unit =
    val repo = ProfessionalRepository.load(professionalsPath, communesPath)
    val date = LocalDate.now()
    val resolus = repo.joinStats.total - repo.joinStats.unresolved

    val contenu =
      s"""# Statistiques de jointure professionnels ↔ communes
         |
         |Mesuré le $date par `ProfessionalRepository.load`. Régénéré par
         |`sbt "server/runMain fr.ipssi.healthmap.server.data.pipeline.GenerateJoinReport"`
         |après toute mise à jour des Parquet sources — ne pas éditer à la main.
         |""".stripMargin +
        s"\n## Grain résolu (${formatN(repo.joinStats.total)} professionnels avec code postal et commune, GPS ou non)\n\n" +
        s"Alimente `professions`, `total`, région, département, couverture, densité,\n" +
        s"après exclusion des non-résolus (${formatN(resolus)} professionnels).\n\n" +
        table(repo.joinStats) + "\n\n" +
        s"## Grain géolocalisé (${formatN(repo.joinStatsGeolocalise.total)} professionnels avec coordonnées GPS)\n\n" +
        s"Alimente la carte (`/api/map`) et le classement des communes\n" +
        s"(`/api/top-communes`) — sous-ensemble du grain résolu.\n\n" +
        table(repo.joinStatsGeolocalise) + "\n"

    Files.writeString(outPath, contenu)
    println(s"écrit : $outPath")
