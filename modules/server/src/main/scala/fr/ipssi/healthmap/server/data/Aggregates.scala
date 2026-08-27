package fr.ipssi.healthmap.server.data

/** Statistiques de qualité de la jointure professionnels ↔ communes,
  * mesurées une fois à la construction du repository — les chiffres cités
  * dans les échanges d'équipe doivent provenir d'ici, pas d'une estimation.
  */
case class JoinStats(
  total: Long,
  exact: Long,
  fallback: Long,
  unresolved: Long
):
  private def pct(n: Long): Double = if total == 0 then 0.0 else math.round(n * 1000.0 / total) / 10.0
  def exactRate: Double      = pct(exact)
  def fallbackRate: Double   = pct(fallback)
  def unresolvedRate: Double = pct(unresolved)

  override def toString: String =
    s"JoinStats(total=$total, exact=$exact [$exactRate%], fallback=$fallback [$fallbackRate%], unresolved=$unresolved [$unresolvedRate%])"

/** Effectif et densité pour 100 000 habitants d'une zone (commune,
  * département ou région).
  */
case class DensityStat(
  code: String,
  nom: String,
  effectif: Int,
  population: Long,
  pour100k: Double
)

/** Effectif et densité pour 100 000 habitants par type de territoire INSEE
  * (`grille_densite_texte` : grands centres urbains, ceintures urbaines,
  * bourgs ruraux, etc.).
  */
case class GrilleDensityStat(
  grille: String,
  effectif: Int,
  population: Long,
  pour100k: Double
)
