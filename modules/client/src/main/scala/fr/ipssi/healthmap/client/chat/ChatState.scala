package fr.ipssi.healthmap.client.chat

import com.raquo.laminar.api.L.*
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

import fr.ipssi.healthmap.client.ui.ApiClient
import fr.ipssi.healthmap.shared.model.{ChatRequest, ChatResponse}

/** Un tour de conversation : la question posée et la réponse de l'assistant. */
final case class Echange(question: String, departement: Option[String], reponse: ChatResponse)

/** État de l'onglet assistant.
  *
  * Construit une seule fois au niveau de l'application et non à chaque
  * activation de l'onglet : l'historique survit ainsi aux allers-retours entre
  * onglets, là où le Python le conservait dans `st.session_state`.
  */
final class ChatState:

  val saisie: Var[String]              = Var("")
  val departement: Var[String]         = Var("")
  val historique: Var[List[Echange]]   = Var(Nil)
  val enCours: Var[Boolean]            = Var(false)
  val erreur: Var[Option[String]]      = Var(None)

  /** Dernier échange, mis en avant sous le formulaire. */
  val dernier: Signal[Option[Echange]] = historique.signal.map(_.headOption)

  /** Vrai quand la question est vide ou qu'une requête est déjà en vol. */
  val envoiImpossible: Signal[Boolean] =
    saisie.signal.combineWith(enCours.signal).map { case (texte, occupe) => texte.trim.isEmpty || occupe }

  /** Pose la question courante à `POST /api/chat`. */
  def envoyer(): Unit =
    val message = saisie.now().trim
    if message.nonEmpty && !enCours.now() then
      val dept = Option(departement.now()).map(_.trim).filter(_.nonEmpty)
      enCours.set(true)
      erreur.set(None)
      ApiClient.chat(ChatRequest(message, dept)).onComplete {
        case Success(reponse) =>
          enCours.set(false)
          historique.update(liste => Echange(message, dept, reponse) :: liste)
          saisie.set("")
        case Failure(e) =>
          enCours.set(false)
          erreur.set(Some(s"Assistant injoignable : ${ApiClient.message(e)}"))
      }

  /** Vide l'historique de la conversation. */
  def effacer(): Unit =
    historique.set(Nil)
    erreur.set(None)
