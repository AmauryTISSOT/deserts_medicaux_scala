package fr.ipssi.healthmap.client.chat

import com.raquo.laminar.api.L.*

import fr.ipssi.healthmap.client.ui.{Format, Widgets}
import fr.ipssi.healthmap.shared.ref.Geo

/** Onglet « Assistant IA ».
  *
  * Portage de l'onglet 4 du Streamlit : description libre des symptômes,
  * département optionnel pour contextualiser la couverture locale, symptômes
  * détectés, spécialités conseillées, réponse du modèle et historique.
  *
  * Le composant ne fait aucune analyse : l'extraction des symptômes et
  * l'orientation vivent dans `shared.ref.Symptoms`, appliquées côté serveur par
  * `OllamaChatService`. Le navigateur ne connaît que le contrat `ChatRequest` et
  * `ChatResponse`.
  */
object ChatTab:

  /** Codes de département proposés dans la liste déroulante. */
  private val departements: List[(String, String)] =
    Geo.deptNames.toList.sortBy(_._1)

  def apply(etat: ChatState): HtmlElement =
    div(
      cls := "hm-tabcontent",
      h2("Assistant santé"),
      p(
        cls := "hm-muted",
        "Décrivez vos symptômes ou vos besoins. L'assistant oriente vers une spécialité ",
        "et situe la couverture médicale de votre département. Il ne remplace pas un avis médical."
      ),
      formulaire(etat),
      child.maybe <-- etat.erreur.signal.map(_.map(message => div(cls := "hm-erreur", message))),
      child.maybe <-- etat.dernier.map(_.map(reponse)),
      historique(etat)
    )

  private def formulaire(etat: ChatState): HtmlElement =
    div(
      cls := "hm-chat-form",
      div(
        cls := "hm-champ",
        label(forId := "hm-dept", "Votre département (optionnel)"),
        select(
          idAttr := "hm-dept",
          controlled(
            value <-- etat.departement.signal,
            onChange.mapToValue --> etat.departement.writer
          ),
          option(value := "", "Non précisé"),
          departements.map { case (code, nom) => option(value := code, s"$code - $nom") }
        )
      ),
      div(
        cls := "hm-champ",
        label(forId := "hm-question", "Décrivez vos symptômes"),
        textArea(
          idAttr := "hm-question",
          rows := 4,
          placeholder := "Exemple : j'ai mal à la tête depuis trois jours et je tousse.",
          controlled(
            value <-- etat.saisie.signal,
            onInput.mapToValue --> etat.saisie.writer
          )
        )
      ),
      div(
        cls := "hm-actions",
        button(
          cls := "hm-primaire",
          typ := "button",
          disabled <-- etat.envoiImpossible,
          child.text <-- etat.enCours.signal.map(c => if c then "Analyse en cours." else "Obtenir une orientation"),
          onClick --> Observer[Any](_ => etat.envoyer())
        ),
        button(
          cls := "hm-lien",
          typ := "button",
          "Effacer l'historique",
          onClick --> Observer[Any](_ => etat.effacer())
        )
      )
    )

  private def reponse(echange: Echange): HtmlElement =
    val r = echange.reponse
    val contexte = echange.departement match
      case Some(code) => s"Question posée avec le département $code (${Geo.departementName(code)}) en contexte."
      case None       => "Question posée sans département de contexte."

    div(
      cls := "hm-reponse",
      cls.toggle("est-urgente") := r.urgence,
      if r.urgence then
        List(
          div(
            cls := "hm-urgence",
            strong("Situation potentiellement urgente."),
            span(
              " Appelez le 15 ou le 112. Pour une urgence non vitale, ",
              "le 116 117 met en relation avec un médecin de garde."
            )
          )
        )
      else Nil,
      div(
        cls := "hm-pastilles",
        bloc("Symptômes détectés", r.symptomes, "Aucun symptôme précis reconnu."),
        bloc("Spécialités conseillées", r.specialites, "Un médecin généraliste reste le bon point d'entrée.")
      ),
      Widgets.section("Analyse"),
      div(cls := "hm-analyse", paragraphes(r.reponse)),
      p(cls := "hm-muted hm-note", contexte)
    )

  private def bloc(titre: String, valeurs: List[String], repli: String): HtmlElement =
    div(
      cls := "hm-bloc",
      span(cls := "hm-bloc-titre", titre),
      if valeurs.isEmpty then p(cls := "hm-muted", repli)
      else div(cls := "hm-pastille-liste", valeurs.map(v => span(cls := "hm-pastille", v)))
    )

  /** Rend la réponse du modèle en paragraphes : elle arrive en texte brut, avec
    * des sauts de ligne significatifs.
    */
  private def paragraphes(texte: String): List[HtmlElement] =
    texte.split('\n').toList.map(_.trim).filter(_.nonEmpty) match
      case Nil    => List(p(cls := "hm-muted", "Réponse vide."))
      case lignes => lignes.map(ligne => p(ligne))

  private def historique(etat: ChatState): HtmlElement =
    div(
      child.maybe <-- etat.historique.signal.map { liste =>
        Option.when(liste.sizeIs > 1)(
          div(
            Widgets.section("Historique"),
            div(
              cls := "hm-historique",
              liste.drop(1).map { echange =>
                div(
                  cls := "hm-echange",
                  p(cls := "hm-echange-question", Format.resume(echange.question, 90)),
                  div(cls := "hm-analyse", paragraphes(echange.reponse.reponse))
                )
              }
            )
          )
        )
      }
    )
