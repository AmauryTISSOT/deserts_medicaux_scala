package fr.ipssi.healthmap.client.map

import com.raquo.laminar.api.L.*
import fr.ipssi.healthmap.client.facade.*
import org.scalajs.dom
import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js
import scala.scalajs.js.Thenable.Implicits.given
import scala.util.{Failure, Success}

/** Fabrique commune aux choroplèthes région et département.
  *
  * Elle télécharge le fond GeoJSON une fois (servi en local par le lot C, ce qui
  * évite le CORS) et teinte chaque entité selon une valeur jointe par une clé.
  * La valeur est un `Double` et son libellé est fourni par la `View` : la même
  * carte affiche indifféremment un effectif brut ou une densité pour 100 000
  * habitants, la bascule étant décidée par l'onglet (lot E). Les entités sans
  * donnée restent en gris neutre.
  */
object Choropleth:

  /** Une entité à colorer : `key` joint le GeoJSON, `label` titre l'infobulle. */
  final case class Datum(key: String, label: String, value: Double)

  /** Ce qu'affiche la carte à un instant donné : les données, le titre de
    * légende et la façon de mettre en forme les valeurs (entier ou densité).
    */
  final case class View(data: List[Datum], legendTitle: String, format: Double => String)

  object View:
    val vide: View = View(Nil, "", ColorScale.entier)

  def build(
      view: Signal[View],
      geoJsonUrl: String,
      joinKey: js.Dynamic => String,
      heightPx: Int = 640
  ): HtmlElement =
    val status = Var[Option[String]](Some("Chargement du fond cartographique…"))

    LeafletMap.container(heightPx, statusSignal = status.signal) { (map, ctx) =>
      var geoData: Option[js.Any] = None
      var layer: Option[GeoJSON]  = None
      var legend: Option[Control] = None
      var current: View           = View.vide

      def redraw(): Unit = geoData.foreach { gj =>
        layer.foreach(map.removeLayer)
        legend.foreach(_.remove())

        val byKey = current.data.map(d => d.key -> d).toMap
        val scale = ColorScale.quantile(current.data.map(_.value), ColorScale.viridis)

        val styleFn: js.Function1[js.Dynamic, js.Any] = feature =>
          val couleur = byKey.get(joinKey(feature)) match
            case Some(d) if d.value > 0 => scale.colorFor(d.value)
            case _                      => ColorScale.noData
          js.Dynamic.literal(
            fillColor = couleur,
            weight = 1,
            color = "#7a7a7a",
            fillOpacity = if byKey.contains(joinKey(feature)) then 0.78 else 0.25,
            opacity = 0.8
          )

        var geo: GeoJSON = null
        val onEach: js.Function2[js.Dynamic, js.Dynamic, Unit] = (feature, featureLayer) =>
          val infobulle = byKey.get(joinKey(feature)) match
            case Some(d) =>
              s"""<strong>${LeafletMap.esc(d.label)}</strong><br>${LeafletMap.esc(current.format(d.value))}"""
            case None =>
              s"""<strong>${LeafletMap.esc(prop(feature, "nom"))}</strong><br>aucune donnée"""
          featureLayer.bindTooltip(infobulle, js.Dynamic.literal(sticky = true))
          featureLayer.on("mouseover", (_: js.Dynamic) => featureLayer.setStyle(js.Dynamic.literal(weight = 2.5, fillOpacity = 0.92)))
          featureLayer.on("mouseout", (_: js.Dynamic) => geo.resetStyle(featureLayer))
          ()

        geo = Leaflet.geoJSON(gj, js.Dynamic.literal(style = styleFn, onEachFeature = onEach))
        geo.addTo(map)
        layer = Some(geo)
        map.fitBounds(geo.getBounds(), js.Dynamic.literal(padding = js.Array(12, 12), animate = false))
        if current.data.nonEmpty then
          legend = Some(LeafletMap.legendControl(current.legendTitle, scale.legend(current.format)).addTo(map))
      }

      view.foreach { v => current = v; redraw() }(ctx.owner)

      dom.fetch(geoJsonUrl).flatMap(_.text()).onComplete {
        case Success(text) =>
          geoData = Some(js.JSON.parse(text))
          status.set(None)
          redraw()
        case Failure(e) =>
          status.set(Some(s"Fond indisponible : ${e.getMessage}"))
      }
    }

  /** Lit une propriété texte d'une entité GeoJSON (`feature.properties.<name>`). */
  def prop(feature: js.Dynamic, name: String): String =
    val v = feature.selectDynamic("properties").selectDynamic(name)
    if js.isUndefined(v) || v == null then "" else v.toString
