package fr.ipssi.healthmap.client.ui

/** Mises en forme partagées par les tableaux, les métriques et l'histogramme.
  *
  * `String.format` n'est pas disponible en Scala.js avec les mêmes garanties
  * qu'en JVM et les locales ne sont pas embarquées : le groupement des milliers
  * est fait à la main, à la française.
  */
object Format:

  /** Entier groupé par milliers avec une espace insécable fine (1 234). */
  def entier(n: Int): String =
    val chiffres = math.abs(n).toString.reverse.grouped(3).mkString(" ").reverse
    if n < 0 then s"-$chiffres" else chiffres

  /** Pourcentage à une décimale. */
  def pourcent(part: Int, total: Int): String =
    if total <= 0 then "-"
    else
      val valeur = math.round(part * 1000.0 / total) / 10.0
      s"$valeur %".replace('.', ',')

  /** Tronque un texte pour un libellé d'historique. */
  def resume(texte: String, taille: Int): String =
    val propre = texte.replace('\n', ' ').trim
    if propre.length <= taille then propre else propre.take(taille).trim + "..."
