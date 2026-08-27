package fr.ipssi.healthmap.client.ui

import com.raquo.laminar.api.L.*
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}

import fr.ipssi.healthmap.shared.model.*

/** État réactif de l'application.
  *
  * Une seule source de vérité pour le filtre profession : là où le Python
  * dupliquait le `multiselect` sur deux onglets — chacun avec son propre état et
  * son propre rechargement du Parquet —, le filtre est ici partagé par tous les
  * onglets et déclenche un unique rechargement des quatre agrégats.
  *
  * Les composants n'observent que des `Signal` ; aucun d'eux ne connaît le
  * transport HTTP.
  */
final class AppState:

  /** Référentiel des professions, alimenté une fois au démarrage. */
  val professions: Var[List[Profession]] = Var(Nil)

  /** Professions retenues. Vide signifie « toutes professions » côté API, mais
    * l'interface part sur « Médecin » comme le faisait le Python.
    */
  val selection: Var[Set[String]] = Var(Set(AppState.professionParDefaut))

  val points: Var[List[MapPoint]]              = Var(Nil)
  val regions: Var[List[RegionCount]]          = Var(Nil)
  val departements: Var[List[DepartementCount]] = Var(Nil)
  val topCommunes: Var[List[CommuneCount]]     = Var(Nil)

  private val requetesEnCours: Var[Int] = Var(0)
  private var generation: Int           = 0

  /** Dernière erreur réseau ou API, affichée en bandeau. */
  val erreur: Var[Option[String]] = Var(None)

  /** Vrai tant qu'au moins une requête est en vol : indicateur de chargement. */
  val chargement: Signal[Boolean] = requetesEnCours.signal.map(_ > 0)

  /** Effectif total du périmètre courant, équivalent du `st.metric` du Python. */
  val effectifTotal: Signal[Int] = points.signal.map(_.map(_.nombrePros).sum)

  /** Nombre de localisations distinctes du périmètre courant. */
  val localisations: Signal[Int] = points.signal.map(_.size)

  /** Libellé du filtre courant, pour les titres et les métriques. */
  val libelleSelection: Signal[String] =
    selection.signal.map(s => if s.isEmpty then "toutes professions" else s.toList.sorted.mkString(", "))

  /** Charge le référentiel des professions et ajuste la sélection par défaut si
    * « Médecin » est absent du jeu de données.
    */
  def demarrer(): Unit =
    requetesEnCours.update(_ + 1)
    ApiClient.professions.onComplete { resultat =>
      requetesEnCours.update(_ - 1)
      resultat match
        case Success(liste) =>
          professions.set(liste)
          if !liste.exists(p => selection.now().contains(p.nom)) then
            selection.set(liste.headOption.map(_.nom).toSet)
        case Failure(e) =>
          erreur.set(Some(s"Référentiel des professions indisponible : ${ApiClient.message(e)}"))
    }

  /** Recharge les quatre agrégats pour un filtre donné.
    *
    * Les réponses d'une requête périmée — l'utilisateur a recoché une case entre
    * temps — sont ignorées grâce au compteur de génération : sans lui, une
    * réponse lente pourrait écraser une réponse plus récente.
    */
  def recharger(filtre: Set[String]): Unit =
    val liste = filtre.toList.sorted
    generation += 1
    val courante = generation
    def actuelle: Boolean = courante == generation

    requetesEnCours.update(_ + 4)
    ApiClient.mapPoints(liste).onComplete(termine(v => if actuelle then points.set(v)))
    ApiClient.regions(liste).onComplete(termine(v => if actuelle then regions.set(v)))
    ApiClient.departements(liste).onComplete(termine(v => if actuelle then departements.set(v)))
    ApiClient.topCommunes(liste, 10).onComplete(termine(v => if actuelle then topCommunes.set(v)))

  /** Coche ou décoche une profession. */
  def basculer(nom: String, retenue: Boolean): Unit =
    selection.update(s => if retenue then s + nom else s - nom)

  /** Retient toutes les professions du référentiel. */
  def toutSelectionner(): Unit =
    selection.set(professions.now().map(_.nom).toSet)

  /** Vide la sélection : l'API répond alors sur l'ensemble du jeu de données. */
  def toutEffacer(): Unit =
    selection.set(Set.empty)

  private def termine[A](succes: A => Unit): Try[A] => Unit = resultat =>
    requetesEnCours.update(_ - 1)
    resultat match
      case Success(valeur) =>
        erreur.set(None)
        succes(valeur)
      case Failure(e) =>
        erreur.set(Some(ApiClient.message(e)))

object AppState:

  /** Profession affichée au premier chargement, comme dans l'application Python. */
  val professionParDefaut = "Médecin"
