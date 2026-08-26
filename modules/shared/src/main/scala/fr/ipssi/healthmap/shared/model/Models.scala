package fr.ipssi.healthmap.shared.model

import upickle.default.{ReadWriter, macroRW}

/** Une profession du référentiel, avec son effectif national. */
case class Profession(nom: String, effectif: Int)
object Profession:
  given ReadWriter[Profession] = macroRW

/** Point de la carte : un code postal agrégé (~6 000 points sur le jeu complet). */
case class MapPoint(
    codePostal: String,
    commune: String,
    latitude: Double,
    longitude: Double,
    nombrePros: Int,
    professions: List[String]
)
object MapPoint:
  given ReadWriter[MapPoint] = macroRW

/** Agrégat régional, clé de jointure avec `properties.nom` du GeoJSON des régions. */
case class RegionCount(nom: String, nombrePros: Int)
object RegionCount:
  given ReadWriter[RegionCount] = macroRW

/** Agrégat départemental, clé de jointure avec `properties.code` du GeoJSON des départements. */
case class DepartementCount(code: String, nom: String, region: String, nombrePros: Int)
object DepartementCount:
  given ReadWriter[DepartementCount] = macroRW

/** Ligne du classement des communes les mieux dotées. */
case class CommuneCount(commune: String, codePostal: String, nombrePros: Int)
object CommuneCount:
  given ReadWriter[CommuneCount] = macroRW

/** Analyse de couverture d'un département, équivalent de `analyze_region_coverage`. */
case class CoverageReport(
    code: String,
    nom: String,
    region: String,
    total: Int,
    parProfession: List[Profession],
    niveau: String
)
object CoverageReport:
  given ReadWriter[CoverageReport] = macroRW

/** Question posée à l'assistant, éventuellement contextualisée sur un département. */
case class ChatRequest(message: String, departement: Option[String] = None)
object ChatRequest:
  given ReadWriter[ChatRequest] = macroRW

/** Réponse de l'assistant : texte, spécialités conseillées, symptômes détectés, drapeau d'urgence. */
case class ChatResponse(
    reponse: String,
    specialites: List[String] = Nil,
    symptomes: List[String] = Nil,
    urgence: Boolean = false
)
object ChatResponse:
  given ReadWriter[ChatResponse] = macroRW

/** Corps des réponses 4xx et 5xx de l'API. */
case class ApiError(message: String)
object ApiError:
  given ReadWriter[ApiError] = macroRW
