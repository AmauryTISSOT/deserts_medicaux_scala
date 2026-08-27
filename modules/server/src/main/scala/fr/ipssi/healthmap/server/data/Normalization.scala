package fr.ipssi.healthmap.server.data

/** Normalisation des noms de commune pour la jointure professionnels ↔
  * référentiel communes.
  *
  * Règles (mesurées sur le jeu réel avant application, voir
  * `pipeline.JoinRateReport`) : minuscules, accents retirés, tirets et
  * apostrophes traités comme des séparateurs, abréviations "St"/"Ste"
  * développées en "Saint"/"Sainte" (+8,70 points de correspondance exacte
  * mesurés), espaces multiples réduits.
  */
object Normalization:

  /** Expression SQL DuckDB normalisant la colonne `col`. */
  def sql(col: String): String =
    val lower    = s"lower(strip_accents($col))"
    val seps     = s"regexp_replace($lower, '[-'' ]+', ' ', 'g')"
    // "ste" doit être développé avant "st" (sinon "sainte" -> "sainet" via un
    // remplacement partiel de "st").
    val steExp   = s"regexp_replace($seps, '(^|\\s)ste(\\s|$$)', '\\1sainte\\2', 'g')"
    val stExp    = s"regexp_replace($steExp, '(^|\\s)st(\\s|$$)', '\\1saint\\2', 'g')"
    s"trim(regexp_replace($stExp, '\\s+', ' ', 'g'))"
