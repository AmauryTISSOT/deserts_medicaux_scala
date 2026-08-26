package fr.ipssi.healthmap.shared.ref

/** Référentiel d'orientation symptôme → spécialités.
  *
  * Porté depuis `HealthMapChatbot.SYMPTOMS_TO_SPECIALTIES`. Placé dans `shared`
  * pour que le client puisse proposer des suggestions sans aller-retour serveur.
  */
object Symptoms:

  val symptomsToSpecialties: Map[String, List[String]] = Map(
    "mal de tête"  -> List("généraliste", "cardiologue", "neurologue"),
    "migraines"    -> List("neurologue", "généraliste"),
    "mal de dents" -> List("dentiste"),
    "mal au ventre" -> List("généraliste", "gastro-entérologue"),
    "douleur"      -> List("généraliste", "rhumatologue", "kinésithérapeute"),
    "grippe"       -> List("généraliste"),
    "rhume"        -> List("généraliste"),
    "toux"         -> List("généraliste", "pneumologue"),
    "fièvre"       -> List("généraliste"),
    "cœur"         -> List("cardiologue", "généraliste"),
    "tensio"       -> List("cardiologue", "généraliste"),
    "diabète"      -> List("endocrinologue", "généraliste"),
    "peau"         -> List("dermatologue", "généraliste"),
    "yeux"         -> List("ophtalmologue"),
    "oreilles"     -> List("oto-rhino", "généraliste"),
    "articulation" -> List("rhumatologue", "kinésithérapeute"),
    "dos"          -> List("rhumatologue", "kinésithérapeute", "généraliste"),
    "jambes"       -> List("angiologue", "phlébologue", "kinésithérapeute"),
    "stress"       -> List("psychiatre", "généraliste", "psychologue"),
    "dépression"   -> List("psychiatre", "psychologue", "généraliste"),
    "anxiété"      -> List("psychiatre", "psychologue", "généraliste"),
    "allergie"     -> List("allergologue", "généraliste"),
    "grossesse"    -> List("gynécologue", "généraliste"),
    "gynéco"       -> List("gynécologue")
  )

  /** Mots-clés dont la présence justifie une réponse d'urgence plutôt qu'une orientation. */
  val emergencyKeywords: List[String] =
    List("urgence", "urgent", "grave", "sang", "inconscient", "respirer", "étouffe", "malaise", "accident")

  /** Symptômes reconnus dans un message libre, insensible à la casse. */
  def extract(message: String): List[String] =
    val lower = message.toLowerCase
    symptomsToSpecialties.keys.filter(k => lower.contains(k)).toList.sorted

  /** Spécialités conseillées pour un message libre, dédoublonnées et ordonnées. */
  def specialtiesFor(message: String): List[String] =
    extract(message).flatMap(symptomsToSpecialties).distinct

  /** Vrai si le message doit déclencher la réponse d'urgence. */
  def isEmergency(message: String): Boolean =
    val lower = message.toLowerCase
    emergencyKeywords.exists(lower.contains)
