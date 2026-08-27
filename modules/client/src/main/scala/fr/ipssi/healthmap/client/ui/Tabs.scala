package fr.ipssi.healthmap.client.ui

import com.raquo.laminar.api.L.*

/** Barre d'onglets et zone de contenu.
  *
  * Le contenu est reconstruit à chaque activation plutôt que masqué en CSS :
  * c'est ce que demandent les composants du lot D, dont les cartes Leaflet se
  * créent au montage et s'effacent au démontage. Une carte laissée dans un
  * conteneur `display:none` se retrouverait dimensionnée à zéro.
  */
object Tabs:

  /** @param id      identifiant stable, utilisé comme valeur du `Var` d'onglet actif
    * @param libelle texte du bouton
    * @param contenu construit à la demande, à chaque activation de l'onglet
    */
  final case class Tab(id: String, libelle: String, contenu: () => HtmlElement)

  def apply(onglets: List[Tab], actif: Var[String]): HtmlElement =
    div(
      cls := "hm-tabs",
      div(
        cls := "hm-tabbar",
        onglets.map { onglet =>
          button(
            cls := "hm-tabbtn",
            cls.toggle("est-actif") <-- actif.signal.map(_ == onglet.id),
            typ := "button",
            onglet.libelle,
            onClick.mapTo(onglet.id) --> actif.writer
          )
        }
      ),
      div(
        cls := "hm-tabpanel",
        child <-- actif.signal.distinct.map { id =>
          onglets.find(_.id == id).map(_.contenu()).getOrElse(div())
        }
      )
    )
