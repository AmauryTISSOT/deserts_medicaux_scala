package fr.ipssi.healthmap.client.map

/** Échelle de couleur séquentielle par quantiles et modèle de légende associé.
  *
  * Logique pure (aucune dépendance Leaflet ni DOM) partagée par la couche de
  * points et les deux choroplèthes. On reprend l'esprit du Python d'origine —
  * Plasma pour les points, Viridis pour les choroplèthes — mais en classes
  * discrètes : la légende est alors exacte plutôt qu'une barre continue.
  */
object ColorScale:

  /** Approximation Plasma, foncé → clair (couche de points). */
  val plasma: Vector[String] =
    Vector("#0d0887", "#5c01a6", "#9c179e", "#cc4778", "#ed7953", "#fdb42f", "#f0f921")

  /** Approximation Viridis, foncé → clair (choroplèthes région et département). */
  val viridis: Vector[String] =
    Vector("#440154", "#443983", "#31688e", "#21918c", "#35b779", "#90d743", "#fde725")

  /** Couleur des entités sans donnée (région/département à effectif nul). */
  val noData: String = "#d9d6d0"

  /** Échelle discrète.
    *
    * @param breaks bornes supérieures (incluses) des `colors.size - 1` premières
    *               classes ; la dernière classe est ouverte vers le haut.
    * @param colors une couleur par classe, de la plus faible à la plus forte.
    */
  final case class Scale(breaks: Vector[Int], colors: Vector[String]):

    /** Couleur d'une valeur : première classe dont la borne supérieure la couvre. */
    def colorFor(value: Int): String =
      val idx = breaks.indexWhere(value <= _)
      colors(if idx < 0 then colors.size - 1 else idx)

    /** Légende, de la classe la plus faible à la plus forte : `(libellé, couleur)`. */
    def legend: List[(String, String)] =
      colors.indices.toList.map { i =>
        val label =
          if breaks.isEmpty then fmt(0) + " et plus"
          else if i == 0 then s"≤ ${fmt(breaks(0))}"
          else if i == colors.size - 1 then s"≥ ${fmt(breaks(i - 1) + 1)}"
          else s"${fmt(breaks(i - 1) + 1)} – ${fmt(breaks(i))}"
        label -> colors(i)
      }

  /** Construit une échelle par quantiles à partir des valeurs observées.
    *
    * Chaque classe couvre environ `1 / palette.size` des valeurs. Les bornes en
    * doublon (jeux de données peu variés) sont fusionnées, ce qui réduit
    * proprement le nombre de classes et la légende avec.
    */
  def quantile(values: Iterable[Int], palette: Vector[String]): Scale =
    val sorted = values.filter(_ > 0).toVector.sorted
    if sorted.isEmpty then Scale(Vector.empty, Vector(palette.last))
    else
      val n = sorted.size
      val k = palette.size
      val breaks =
        (1 until k)
          .map(i => sorted(math.min(((i.toDouble / k) * n).toInt, n - 1)))
          .filter(_ > sorted.head)
          .distinct
          .toVector
      Scale(breaks, pickEven(palette, breaks.size + 1))

  /** Prélève `count` couleurs réparties régulièrement dans la palette. */
  private def pickEven(palette: Vector[String], count: Int): Vector[String] =
    if count <= 1 then Vector(palette.last)
    else if count >= palette.size then palette
    else Vector.tabulate(count)(j => palette(math.round(j.toDouble * (palette.size - 1) / (count - 1)).toInt))

  /** Entier groupé par milliers avec une espace fine, à la française (1 234). */
  private def fmt(n: Int): String =
    val s = math.abs(n).toString
    val grouped = s.reverse.grouped(3).mkString(" ").reverse
    if n < 0 then s"-$grouped" else grouped
