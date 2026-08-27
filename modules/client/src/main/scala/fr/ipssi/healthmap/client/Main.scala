package fr.ipssi.healthmap.client

import com.raquo.laminar.api.L.*
import org.scalajs.dom

import fr.ipssi.healthmap.client.ui.App

/** Point d'entrée du client.
  *
  * La vitrine du lot D (`map.MapShowcase`, alimentée par `map.SampleData`) reste
  * dans le dépôt comme banc d'essai des composants de carte sans serveur ; c'est
  * désormais la coquille à quatre onglets du lot E qui est montée, et elle
  * consomme les agrégats réels de l'API.
  */
object Main:

  def main(args: Array[String]): Unit =
    renderOnDomContentLoaded(dom.document.getElementById("app"), App())
