package fr.ipssi.healthmap.client.ui

import com.raquo.laminar.api.L.*

import fr.ipssi.healthmap.shared.model.Profession

/** Filtre profession, équivalent réactif du `st.multiselect` du Python.
  *
  * Une case à cocher par profession du référentiel, l'effectif national en
  * regard. Le composant est sans état propre : il lit et écrit `AppState`, si
  * bien que les deux onglets cartographiques partagent la même sélection au lieu
  * d'en tenir chacun une copie.
  */
object ProfessionFilter:

  def apply(etat: AppState): HtmlElement =
    div(
      cls := "hm-filtre",
      div(
        cls := "hm-filtre-tete",
        span(cls := "hm-filtre-titre", "Filtrer par profession"),
        button(
          cls := "hm-lien",
          typ := "button",
          "Toutes",
          onClick --> Observer[Any](_ => etat.toutSelectionner())
        ),
        button(
          cls := "hm-lien",
          typ := "button",
          "Aucune",
          onClick --> Observer[Any](_ => etat.toutEffacer())
        )
      ),
      child <-- etat.professions.signal.map {
        case Nil   => p(cls := "hm-muted", "Chargement du référentiel des professions.")
        case liste => div(cls := "hm-chips", liste.map(chip(etat, _)))
      },
      p(
        cls := "hm-muted hm-note",
        child.text <-- etat.selection.signal.map { s =>
          if s.isEmpty then "Aucune profession cochée : l'API répond sur l'ensemble du jeu de données."
          else s"${s.size} profession(s) retenue(s)."
        }
      )
    )

  private def chip(etat: AppState, profession: Profession): HtmlElement =
    label(
      cls := "hm-chip",
      cls.toggle("est-active") <-- etat.selection.signal.map(_.contains(profession.nom)),
      input(
        typ := "checkbox",
        checked <-- etat.selection.signal.map(_.contains(profession.nom)),
        onInput.mapToChecked --> Observer[Boolean](coche => etat.basculer(profession.nom, coche))
      ),
      span(cls := "hm-chip-nom", profession.nom),
      span(cls := "hm-chip-nb", Format.entier(profession.effectif))
    )
