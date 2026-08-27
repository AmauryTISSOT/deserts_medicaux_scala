package fr.ipssi.healthmap.client.ui

import com.raquo.laminar.api.L.*

/** Onglet « Infos » : présentation du projet, sources de données et écarts
  * assumés avec l'application Python d'origine.
  *
  * Contenu statique, porté depuis l'onglet 3 du Streamlit, augmenté du tableau
  * des corrections apportées au passage — c'est le support le plus direct pour
  * la soutenance.
  */
object InfoTab:

  def apply(): HtmlElement =
    div(
      cls := "hm-tabcontent hm-prose",
      h2("À propos de HealthMap"),
      p(
        "HealthMap cartographie la répartition des professionnels de santé en France ",
        "et oriente vers la bonne spécialité à partir de symptômes décrits en langage libre. ",
        "Cette version est un portage complet en Scala 3 de l'application Python et Streamlit d'origine."
      ),
      Widgets.section("Fonctionnalités"),
      ul(
        li("Carte des professionnels par code postal, avec regroupement des points et infobulles."),
        li("Choroplèthes par région et par département, jointes aux fonds GeoJSON servis en local."),
        li("Classement des communes les mieux dotées et tableaux triables par région et par département."),
        li("Histogramme des effectifs départementaux."),
        li("Assistant d'orientation adossé à Ollama, avec repli sur le référentiel symptômes vers spécialités.")
      ),
      Widgets.section("Architecture"),
      ul(
        li(
          strong("shared"),
          " : modèles, codecs upickle, liste des endpoints et référentiels géographiques, ",
          "compilés à la fois pour la JVM et pour le navigateur."
        ),
        li(strong("server"), " : http4s Ember, agrégats DuckDB, proxy et cache des fonds GeoJSON, client Ollama."),
        li(strong("client"), " : Scala.js et Laminar, façade Leaflet pour les cartes.")
      ),
      p(
        "Le typage est partagé de bout en bout : une réponse d'API est décodée côté navigateur ",
        "avec la classe même que le serveur a sérialisée, et une modification de signature casse ",
        "la compilation des deux côtés plutôt que de se manifester à l'exécution."
      ),
      Widgets.section("Sources de données"),
      ul(
        li("Annuaire santé de la Cnam : 432 015 professionnels de santé géolocalisés."),
        li("Communes et villes de France : codes postaux et coordonnées GPS."),
        li("France GeoJSON de Grégoire David : contours des régions et des départements.")
      ),
      Widgets.section("Écarts assumés avec la version Python"),
      div(
        cls := "hm-tablewrap",
        table(
          cls := "hm-table",
          thead(tr(th("Correction"), th("Motif"))),
          tbody(
            ecart(
              "La Corse est rattachée à 2A ou 2B selon le seuil 20200",
              "le Python testait un préfixe \"2A\" sur des codes postaux strictement numériques : " +
                "la Corse n'apparaissait jamais sur la choroplèthe."
            ),
            ecart(
              "Le département 79 est libellé Deux-Sèvres",
              "il portait le nom Nièvre, déjà attribué au 58."
            ),
            ecart(
              "Une seule conversion code postal vers département",
              "le Python en avait deux implémentations contradictoires, dans deux fichiers différents."
            ),
            ecart(
              "Fonds GeoJSON téléchargés une fois puis servis en local",
              "le Python rejouait un appel réseau vers GitHub à chaque interaction."
            ),
            ecart(
              "Agrégats calculés une fois au démarrage",
              "le Python rechargeait l'intégralité du fichier Parquet à chaque onglet et à chaque rerun."
            ),
            ecart(
              "Filtre profession unique et partagé",
              "le Python en dupliquait un par onglet, avec deux états indépendants."
            )
          )
        )
      ),
      Widgets.section("Mesure de désert médical"),
      p(
        "Les ",
        strong("effectifs bruts"),
        " placent mécaniquement les zones peuplées en tête : Paris arrive première parce qu'elle est peuplée, ",
        "pas parce qu'elle est mieux dotée. L'onglet « Régions et départements » propose donc une bascule vers la ",
        strong("densité pour 100 000 habitants"),
        ", rapportée à la population INSEE des communes — la vraie mesure de désert médical, indépendante de la taille de la population."
      ),
      Widgets.section("Équipe"),
      p("Adrien Fouquet, Amaury Tissot, Léa Druffin, Satya Minguez, Frédéric Fernandes Da Costa.")
    )

  private def ecart(correction: String, motif: String): HtmlElement =
    tr(td(correction), td(cls := "hm-muted", motif))
