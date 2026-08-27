package fr.ipssi.healthmap.shared.api

/** Contrat d'API partagé : le serveur monte ces chemins, le client les appelle.
  *
  * Toute modification de cette liste est une modification du contrat d'équipe et
  * doit être annoncée avant d'être poussée sur `main`.
  */
object Endpoints:

  val base = "/api"

  /** Liste des professions et leurs effectifs. */
  val professions = s"$base/professions"

  /** Agrégat par code postal pour la couche de points. */
  def map(professions: Seq[String] = Nil): String =
    withProfessions(s"$base/map", professions)

  /** Agrégat par région pour la choroplèthe régionale. */
  def regions(professions: Seq[String] = Nil): String =
    withProfessions(s"$base/regions", professions)

  /** Agrégat par département pour la choroplèthe départementale et l'histogramme. */
  def departements(professions: Seq[String] = Nil): String =
    withProfessions(s"$base/departements", professions)

  /** Densité pour 100 000 habitants par région (choroplèthe régionale, mode densité). */
  def densityRegions(professions: Seq[String] = Nil): String =
    withProfessions(s"$base/density/regions", professions)

  /** Densité pour 100 000 habitants par département (choroplèthe départementale, mode densité). */
  def densityDepartements(professions: Seq[String] = Nil): String =
    withProfessions(s"$base/density/departements", professions)

  /** Classement des communes les mieux dotées. */
  def topCommunes(professions: Seq[String] = Nil, limit: Int = 10): String =
    val q = withProfessions(s"$base/top-communes", professions)
    if q.contains('?') then s"$q&limit=$limit" else s"$q?limit=$limit"

  /** Analyse de couverture d'un département. */
  def coverage(dept: String): String = s"$base/coverage/$dept"

  /** Question à l'assistant IA (POST). */
  val chat = s"$base/chat"

  /** GeoJSON servis en local : `regions` ou `departements`. */
  def geoJson(name: String): String = s"/geo/$name.geojson"

  /** Les deux fonds cartographiques attendus par le lot D. */
  val geoJsonNames: List[String] = List("regions", "departements")

  private def withProfessions(path: String, professions: Seq[String]): String =
    if professions.isEmpty then path
    else s"$path?professions=${professions.map(encode).mkString(",")}"

  /** Encodage d'URL minimal, suffisant pour des libellés de profession.
    *
    * L'encodage UTF-8 est fait à la main : `String.getBytes` n'existe pas en Scala.js
    * et ce code est compilé pour les deux plateformes.
    */
  private def encode(s: String): String =
    val sb = new StringBuilder
    s.foreach: c =>
      if c < 128 && (c.isLetterOrDigit || "-_.~".contains(c)) then sb.append(c)
      else utf8(c).foreach(b => sb.append("%%%02X".format(b)))
    sb.toString

  private def utf8(c: Char): List[Int] =
    val n = c.toInt
    if n < 0x80 then List(n)
    else if n < 0x800 then List(0xc0 | (n >> 6), 0x80 | (n & 0x3f))
    else List(0xe0 | (n >> 12), 0x80 | ((n >> 6) & 0x3f), 0x80 | (n & 0x3f))
