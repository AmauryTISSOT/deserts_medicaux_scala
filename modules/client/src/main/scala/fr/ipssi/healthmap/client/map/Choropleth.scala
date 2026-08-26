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
  * évite le CORS), teinte chaque entité selon l'effectif joint par une clé, et
  * redessine à chaque changement du `Signal` de données. Les entités sans donnée
  * restent en gris neutre.
  */
object Choropleth:

  /** @param joinKey  clé de jointure lue dans `feature.properties`
    * @param keyOf    même clé, extraite du modèle
    * @param countOf  effectif porté par le modèle
    * @param tooltip  infobulle HTML, depuis `(properties, effectif)`
    */
  def build[A](
      data: Signal[List[A]],
      geoJsonUrl: String,
      joinKey: js.Dynamic => String,
      keyOf: A => String,
      countOf: A => Int,
      legendTitle: String,
      tooltip: (js.Dynamic, Int) => String,
      heightPx: Int = 640
  ): HtmlElement =
    val status = Var[Option[String]](Some("Chargement du fond cartographique…"))

    LeafletMap.container(heightPx, statusSignal = status.signal) { (map, ctx) =>
      var geoData: Option[js.Any] = None
      var layer: Option[GeoJSON]  = None
      var legend: Option[Control] = None
      var current: List[A]        = Nil

      def redraw(): Unit = geoData.foreach { gj =>
        layer.foreach(map.removeLayer)
        legend.foreach(_.remove())

        val counts = current.map(a => keyOf(a) -> countOf(a)).toMap
        val scale  = ColorScale.quantile(counts.values, ColorScale.viridis)

        val styleFn: js.Function1[js.Dynamic, js.Any] = feature =>
          val c = counts.getOrElse(joinKey(feature), 0)
          js.Dynamic.literal(
            fillColor = if c > 0 then scale.colorFor(c) else ColorScale.noData,
            weight = 1,
            color = "#7a7a7a",
            fillOpacity = if c > 0 then 0.78 else 0.25,
            opacity = 0.8
          )

        var geo: GeoJSON = null
        val onEach: js.Function2[js.Dynamic, js.Dynamic, Unit] = (feature, featureLayer) =>
          val c = counts.getOrElse(joinKey(feature), 0)
          featureLayer.bindTooltip(tooltip(feature, c), js.Dynamic.literal(sticky = true))
          featureLayer.on("mouseover", (_: js.Dynamic) => featureLayer.setStyle(js.Dynamic.literal(weight = 2.5, fillOpacity = 0.92)))
          featureLayer.on("mouseout", (_: js.Dynamic) => geo.resetStyle(featureLayer))
          ()

        geo = Leaflet.geoJSON(gj, js.Dynamic.literal(style = styleFn, onEachFeature = onEach))
        geo.addTo(map)
        layer = Some(geo)
        map.fitBounds(geo.getBounds(), js.Dynamic.literal(padding = js.Array(12, 12), animate = false))
        if current.nonEmpty then
          legend = Some(LeafletMap.legendControl(legendTitle, scale.legend).addTo(map))
      }

      data.foreach { d => current = d; redraw() }(ctx.owner)

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
