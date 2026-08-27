package fr.ipssi.healthmap.client.ui

import com.raquo.laminar.api.L.*

/** Petits composants transverses : métriques, bandeaux d'état, titres de section. */
object Widgets:

  /** Métrique chiffrée, équivalent du `st.metric` du Python. */
  def metrique(libelle: String, valeur: Signal[String], precision: Signal[String] = Signal.fromValue("")): HtmlElement =
    div(
      cls := "hm-metrique",
      span(cls := "hm-metrique-libelle", libelle),
      span(cls := "hm-metrique-valeur", child.text <-- valeur),
      span(cls := "hm-metrique-precision", child.text <-- precision)
    )

  /** Bandeau de chargement, affiché tant qu'une requête est en vol. */
  def chargement(actif: Signal[Boolean]): HtmlElement =
    div(
      cls := "hm-chargement",
      cls.toggle("est-visible") <-- actif,
      span(cls := "hm-spinner"),
      span("Chargement des agrégats.")
    )

  /** Bandeau d'erreur réseau ou API. */
  def erreur(message: Signal[Option[String]]): HtmlElement =
    div(
      child.maybe <-- message.map(_.map(texte => div(cls := "hm-erreur", texte)))
    )

  /** Titre de section avec sous-titre optionnel. */
  def section(titre: String, sousTitre: String = ""): HtmlElement =
    div(
      cls := "hm-section",
      h3(titre),
      if sousTitre.isEmpty then Nil else List(p(cls := "hm-muted", sousTitre))
    )
