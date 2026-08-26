package fr.ipssi.healthmap.server.chat

import cats.effect.IO
import fr.ipssi.healthmap.server.data.ProfessionalSource
import fr.ipssi.healthmap.shared.model.{ChatRequest, ChatResponse}
import fr.ipssi.healthmap.shared.ref.{Geo, Symptoms}

/** Assistant d'orientation. Le lot E remplace l'implémentation par le client Ollama. */
trait ChatService:
  def respond(request: ChatRequest): IO[ChatResponse]

object ChatService:

  /** Réponse construite sans modèle de langage, à partir des seuls référentiels.
    *
    * Elle reproduit la structure de `generate_response` — détection des symptômes,
    * spécialités conseillées, contexte de couverture départementale, message
    * d'urgence — et reste le repli du serveur quand Ollama est injoignable.
    */
  def rulesBased(source: ProfessionalSource): ChatService = new ChatService:

    def respond(request: ChatRequest): IO[ChatResponse] = IO:
      val message = request.message.trim
      if Symptoms.isEmergency(message) then emergency
      else
        val symptomes   = Symptoms.extract(message)
        val specialites = Symptoms.specialtiesFor(message)
        ChatResponse(
          reponse = texte(message, symptomes, specialites, request.departement),
          specialites = specialites,
          symptomes = symptomes,
          urgence = false
        )

    private def texte(
        message: String,
        symptomes: List[String],
        specialites: List[String],
        departement: Option[String]
    ): String =
      val corps =
        if message.isEmpty then "Décrivez vos symptômes et je vous orienterai vers la bonne spécialité."
        else if symptomes.isEmpty then
          "Je n'ai pas reconnu de symptôme précis dans votre message. " +
            "Un médecin généraliste reste le bon point d'entrée pour un premier avis."
        else
          s"Symptômes identifiés : ${symptomes.mkString(", ")}. " +
            s"Spécialités conseillées : ${specialites.mkString(", ")}."
      (corps :: departement.toList.flatMap(contexte)).mkString(" ")

    private def contexte(code: String): Option[String] =
      source.coverage(code).map { c =>
        s"Dans le département ${c.nom} (${c.region}), ${c.total} professionnels sont recensés : ${c.niveau}."
      }

  /** Réponse d'urgence, portée depuis `get_emergency_help`. */
  val emergency: ChatResponse = ChatResponse(
    reponse =
      "Votre message évoque une situation urgente. Appelez le 15 (SAMU) ou le 112 depuis un téléphone. " +
        "Pour une urgence non vitale, le 116 117 met en relation avec un médecin de garde. " +
        "Cet assistant ne remplace pas un avis médical.",
    urgence = true
  )

  /** Départements connus du référentiel, exposés pour valider le paramètre de contexte. */
  def isKnownDepartement(code: String): Boolean = Geo.deptNames.contains(code)
