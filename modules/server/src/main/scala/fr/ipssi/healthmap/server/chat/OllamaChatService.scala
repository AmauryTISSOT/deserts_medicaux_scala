package fr.ipssi.healthmap.server.chat

import cats.effect.IO

import fr.ipssi.healthmap.server.data.ProfessionalSource
import fr.ipssi.healthmap.shared.model.{ChatRequest, ChatResponse, CoverageReport}
import fr.ipssi.healthmap.shared.ref.Symptoms

/** Assistant d'orientation adossé à Ollama.
  *
  * Portage de `HealthMapChatbot.generate_response` : extraction des symptômes,
  * correspondance vers les spécialités, invite envoyée au modèle `mistral`, et
  * analyse de la couverture du département fourni en contexte.
  *
  * Deux différences avec le Python, toutes deux volontaires :
  *
  *   - l'urgence est détectée avant l'appel au modèle et court-circuite la
  *     génération, là où le Python cherchait le mot « urgent » dans la réponse
  *     déjà produite — un aller-retour de trente secondes pour un message qui
  *     doit s'afficher immédiatement ;
  *   - quand Ollama est injoignable, la réponse n'est pas un message d'erreur
  *     seul : l'orientation par référentiel (`ChatService.rulesBased`) prend le
  *     relais, précédée de l'avertissement. L'application reste utilisable sans
  *     modèle installé, ce qui compte pour la démonstration.
  */
object OllamaChatService:

  def apply(source: ProfessionalSource, ollama: OllamaClient): ChatService =
    new ChatService:

      private val repli = ChatService.rulesBased(source)

      def respond(request: ChatRequest): IO[ChatResponse] =
        val message = request.message.trim
        if Symptoms.isEmergency(message) then IO.pure(ChatService.emergency)
        else
          val symptomes   = Symptoms.extract(message)
          val specialites = Symptoms.specialtiesFor(message)
          val couverture  = request.departement.flatMap(source.coverage)

          ollama.generate(invite(message, symptomes, specialites, couverture)).flatMap {
            case Right(texte) =>
              IO.pure(
                ChatResponse(
                  reponse = (texte :: couverture.map(contexte).toList).mkString("\n\n"),
                  specialites = specialites,
                  symptomes = symptomes,
                  urgence = false
                )
              )
            case Left(avertissement) =>
              repli.respond(request).map(r => r.copy(reponse = s"$avertissement\n\n${r.reponse}"))
          }

  /** Invite envoyée au modèle, portée depuis `aia_prompt`.
    *
    * La couverture départementale y est injectée : le Python la calculait sans
    * jamais la donner au modèle, si bien que la réponse ignorait le contexte
    * local affiché juste à côté d'elle.
    */
  private def invite(
      message: String,
      symptomes: List[String],
      specialites: List[String],
      couverture: Option[CoverageReport]
  ): String =
    val listeSymptomes =
      if symptomes.isEmpty then "aucun symptôme spécifique" else symptomes.mkString(", ")
    val listeSpecialites =
      if specialites.isEmpty then "généraliste" else specialites.mkString(", ")
    val local = couverture.map(c => s"\nContexte local : ${contexte(c)}").getOrElse("")

    s"""Tu es un assistant santé expert en orientation médicale en France.
       |L'utilisateur dit : "$message"
       |
       |Symptômes détectés : $listeSymptomes
       |Spécialités recommandées : $listeSpecialites$local
       |
       |Fournis :
       |1. Un point préliminaire, en rappelant que ce n'est pas un avis médical
       |2. Les raisons des spécialités recommandées
       |3. Des conseils immédiats simples
       |4. Le niveau d'urgence (normal, modéré, urgent avec appel au 15)
       |
       |Sois concis, empathique et clair. Réponds en français.""".stripMargin

  /** Phrase de couverture départementale, reprise de `analyze_region_coverage`. */
  private def contexte(c: CoverageReport): String =
    s"dans le département ${c.nom} (${c.region}), ${c.total} professionnels sont recensés, ${c.niveau}."
