package fr.ipssi.healthmap.client.ui

import com.raquo.laminar.api.L.*

import fr.ipssi.healthmap.client.chat.{ChatState, ChatTab}

/** Coquille de l'application : en-tête, barre d'onglets et zone de contenu.
  *
  * C'est le point d'accueil des composants du lot D — la couche de points va
  * dans l'onglet « Carte », les deux choroplèthes dans l'onglet « Régions et
  * départements » — et le seul endroit où l'état global est construit.
  *
  * Le rechargement des agrégats est branché ici, sur le `Signal` de sélection :
  * cocher une profession dans n'importe quel onglet déclenche un unique
  * rechargement, et tous les onglets le voient.
  */
object App:

  def apply(): HtmlElement =
    val etat     = AppState()
    val chat     = ChatState()
    val onglet   = Var("carte")

    div(
      cls := "hm-app",
      onMountCallback(_ => etat.demarrer()),
      etat.selection.signal --> Observer[Set[String]](filtre => etat.recharger(filtre)),
      enTete(),
      Widgets.erreur(etat.erreur.signal),
      Tabs(
        List(
          Tabs.Tab("carte", "Carte", () => MapTab(etat)),
          Tabs.Tab("stats", "Régions et départements", () => StatsTab(etat)),
          Tabs.Tab("infos", "Infos", () => InfoTab()),
          Tabs.Tab("assistant", "Assistant IA", () => ChatTab(chat))
        ),
        onglet
      ),
      piedDePage()
    )

  private def enTete(): HtmlElement =
    div(
      cls := "hm-header",
      h1("HealthMap"),
      p(cls := "lede", "Répartition des professionnels de santé en France, portage Scala 3 de l'application Streamlit.")
    )

  private def piedDePage(): HtmlElement =
    div(
      cls := "hm-footer",
      p(
        "Données : annuaire santé de la Cnam et communes de France. ",
        "Fonds cartographiques : France GeoJSON, servis en local par le serveur."
      )
    )
