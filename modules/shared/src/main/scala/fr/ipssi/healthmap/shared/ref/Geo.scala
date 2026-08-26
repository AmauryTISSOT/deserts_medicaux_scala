package fr.ipssi.healthmap.shared.ref

/** Référentiel géographique partagé entre le serveur et le navigateur.
  *
  * Source Python : `get_region_from_cp`, `dept_names` (app_streamlit.py) et
  * `code_postal_to_departement` (utils/data.py). Les trois implémentations
  * étaient incohérentes ; celle-ci est la seule référence du projet.
  */
object Geo:

  val UNKNOWN = "Inconnue"

  /** Noms des départements. Le Python associait `79` à « Nièvre » : corrigé ici en
    * « Deux-Sèvres », `58` restant « Nièvre ».
    */
  val deptNames: Map[String, String] = Map(
    "01" -> "Ain",
    "02" -> "Aisne",
    "03" -> "Allier",
    "04" -> "Alpes-de-Haute-Provence",
    "05" -> "Hautes-Alpes",
    "06" -> "Alpes-Maritimes",
    "07" -> "Ardèche",
    "08" -> "Ardennes",
    "09" -> "Ariège",
    "10" -> "Aube",
    "11" -> "Aude",
    "12" -> "Aveyron",
    "13" -> "Bouches-du-Rhône",
    "14" -> "Calvados",
    "15" -> "Cantal",
    "16" -> "Charente",
    "17" -> "Charente-Maritime",
    "18" -> "Cher",
    "19" -> "Corrèze",
    "2A" -> "Corse-du-Sud",
    "2B" -> "Haute-Corse",
    "21" -> "Côte-d'Or",
    "22" -> "Côtes-d'Armor",
    "23" -> "Creuse",
    "24" -> "Dordogne",
    "25" -> "Doubs",
    "26" -> "Drôme",
    "27" -> "Eure",
    "28" -> "Eure-et-Loir",
    "29" -> "Finistère",
    "30" -> "Gard",
    "31" -> "Haute-Garonne",
    "32" -> "Gers",
    "33" -> "Gironde",
    "34" -> "Hérault",
    "35" -> "Ille-et-Vilaine",
    "36" -> "Indre",
    "37" -> "Indre-et-Loire",
    "38" -> "Isère",
    "39" -> "Jura",
    "40" -> "Landes",
    "41" -> "Loir-et-Cher",
    "42" -> "Loire",
    "43" -> "Haute-Loire",
    "44" -> "Loire-Atlantique",
    "45" -> "Loiret",
    "46" -> "Lot",
    "47" -> "Lot-et-Garonne",
    "48" -> "Lozère",
    "49" -> "Maine-et-Loire",
    "50" -> "Manche",
    "51" -> "Marne",
    "52" -> "Haute-Marne",
    "53" -> "Mayenne",
    "54" -> "Meurthe-et-Moselle",
    "55" -> "Meuse",
    "56" -> "Morbihan",
    "57" -> "Moselle",
    "58" -> "Nièvre",
    "59" -> "Nord",
    "60" -> "Oise",
    "61" -> "Orne",
    "62" -> "Pas-de-Calais",
    "63" -> "Puy-de-Dôme",
    "64" -> "Pyrénées-Atlantiques",
    "65" -> "Hautes-Pyrénées",
    "66" -> "Pyrénées-Orientales",
    "67" -> "Bas-Rhin",
    "68" -> "Haut-Rhin",
    "69" -> "Rhône",
    "70" -> "Haute-Saône",
    "71" -> "Saône-et-Loire",
    "72" -> "Sarthe",
    "73" -> "Savoie",
    "74" -> "Haute-Savoie",
    "75" -> "Paris",
    "76" -> "Seine-Maritime",
    "77" -> "Seine-et-Marne",
    "78" -> "Yvelines",
    "79" -> "Deux-Sèvres",
    "80" -> "Somme",
    "81" -> "Tarn",
    "82" -> "Tarn-et-Garonne",
    "83" -> "Var",
    "84" -> "Vaucluse",
    "85" -> "Vendée",
    "86" -> "Vienne",
    "87" -> "Haute-Vienne",
    "88" -> "Vosges",
    "89" -> "Yonne",
    "90" -> "Territoire de Belfort",
    "91" -> "Essonne",
    "92" -> "Hauts-de-Seine",
    "93" -> "Seine-Saint-Denis",
    "94" -> "Val-de-Marne",
    "95" -> "Val-d'Oise",
    "971" -> "Guadeloupe",
    "972" -> "Martinique",
    "973" -> "Guyane",
    "974" -> "La Réunion",
    "976" -> "Mayotte"
  )

  private val regionMembers: Map[String, List[String]] = Map(
    "Auvergne-Rhône-Alpes" -> List("01", "03", "07", "15", "26", "38", "42", "43", "63", "69", "73", "74"),
    "Bourgogne-Franche-Comté" -> List("21", "25", "39", "58", "70", "71", "89", "90"),
    "Bretagne" -> List("22", "29", "35", "56"),
    "Centre-Val de Loire" -> List("18", "28", "36", "37", "41", "45"),
    "Corse" -> List("2A", "2B"),
    "Grand Est" -> List("08", "10", "51", "52", "54", "55", "57", "67", "68", "88"),
    "Hauts-de-France" -> List("02", "59", "60", "62", "80"),
    "Île-de-France" -> List("75", "77", "78", "91", "92", "93", "94", "95"),
    "Normandie" -> List("14", "27", "50", "61", "76"),
    "Nouvelle-Aquitaine" -> List("16", "17", "19", "23", "24", "33", "40", "47", "64", "79", "86", "87"),
    "Occitanie" -> List("09", "11", "12", "30", "31", "32", "34", "46", "48", "65", "66", "81", "82"),
    "Pays de la Loire" -> List("44", "49", "53", "72", "85"),
    "Provence-Alpes-Côte d'Azur" -> List("04", "05", "06", "13", "83", "84"),
    "Guadeloupe" -> List("971"),
    "Martinique" -> List("972"),
    "Guyane" -> List("973"),
    "La Réunion" -> List("974"),
    "Mayotte" -> List("976")
  )

  /** Département → région. Contrairement au Python, la Corse et les DOM sont couverts. */
  val deptToRegion: Map[String, String] =
    regionMembers.flatMap((region, depts) => depts.map(_ -> region))

  /** Régions métropolitaines et ultramarines, triées, telles qu'elles apparaissent
    * dans `properties.nom` du GeoJSON de référence.
    */
  val regions: List[String] = regionMembers.keys.toList.sorted

  /** Code postal → code département.
    *
    * Règles : DOM sur trois chiffres (97x, 98x), Corse éclatée en 2A / 2B selon le
    * seuil 20200 — le Python testait `cp.startswith("2A")` sur des codes postaux
    * strictement numériques, si bien que la Corse n'apparaissait jamais.
    */
  def departementFromCodePostal(cp: String): Option[String] =
    val c = cp.trim
    if c.length != 5 || !c.forall(_.isDigit) then None
    else if c.startsWith("97") || c.startsWith("98") then Some(c.take(3))
    else if c.startsWith("20") then Some(if c.toInt < 20200 then "2A" else "2B")
    else Some(c.take(2))

  /** Nom lisible d'un département, avec repli sur le code lui-même. */
  def departementName(code: String): String =
    deptNames.getOrElse(code, s"Département $code")

  /** Région d'un département, `Inconnue` si le code n'est pas au référentiel. */
  def regionOf(code: String): String =
    deptToRegion.getOrElse(code, UNKNOWN)

  /** Région d'un code postal, `Inconnue` si le code postal est invalide. */
  def regionFromCodePostal(cp: String): String =
    departementFromCodePostal(cp).map(regionOf).getOrElse(UNKNOWN)
