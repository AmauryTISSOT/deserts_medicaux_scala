package fr.ipssi.healthmap.client.map

import com.raquo.laminar.api.L.*
import fr.ipssi.healthmap.client.facade.*
import fr.ipssi.healthmap.shared.model.MapPoint
import scala.scalajs.js

/** Couche de points groupés, équivalent Leaflet du `scatter_mapbox` du Python.
  *
  * Un marqueur par code postal, dont le rayon croît avec l'effectif (aire ∝
  * effectif, comme `size` chez Plotly) et la couleur suit l'échelle Plasma.
  * Le clustering (greffon markercluster) absorbe les ~6 000 positions du jeu
  * complet sans saturer le navigateur.
  *
  * Autonome : alimenté par un `Signal[List[MapPoint]]`, il se redessine à chaque
  * changement du filtre profession et s'insère tel quel dans un onglet.
  */
object PointMap:

  private val minRadius = 7.0
  private val maxRadius = 34.0

  def apply(points: Signal[List[MapPoint]], heightPx: Int = 640): HtmlElement =
    LeafletMap.container(heightPx) { (map, ctx) =>
      var group: Option[LayerGroup] = None
      var legend: Option[Control]   = None

      points.foreach { pts =>
        group.foreach(map.removeLayer)
        legend.foreach(_.remove())

        val scale = ColorScale.quantile(pts.map(_.nombrePros.toDouble), ColorScale.plasma)
        val lo    = pts.map(_.nombrePros).minOption.getOrElse(0)
        val hi    = pts.map(_.nombrePros).maxOption.getOrElse(0)

        val g = LeafletMap.markerGroup()
        pts.foreach { p =>
          val marker = Leaflet.marker(
            js.Array(p.latitude, p.longitude),
            js.Dynamic.literal(icon = icon(p, scale, lo, hi))
          )
          marker.bindTooltip(tooltip(p), js.Dynamic.literal(direction = "top", offset = js.Array(0, -4)))
          g.addLayer(marker)
        }
        map.addLayer(g)
        group = Some(g)

        if pts.nonEmpty then
          legend = Some(LeafletMap.legendControl("Nb de pros", scale.legend(ColorScale.entier)).addTo(map))
          fit(map, pts)
      }(ctx.owner)
    }

  /** Rayon en pixels : racine carrée de l'effectif normalisé, bornée. */
  private def radius(count: Int, lo: Int, hi: Int): Double =
    if hi <= lo then (minRadius + maxRadius) / 2
    else minRadius + (maxRadius - minRadius) * math.sqrt((count - lo).toDouble / (hi - lo))

  /** Pastille colorée dimensionnée par l'effectif — un `divIcon`, donc clusterable. */
  private def icon(p: MapPoint, scale: ColorScale.Scale, lo: Int, hi: Int): DivIcon =
    val r     = radius(p.nombrePros, lo, hi)
    val color = scale.colorFor(p.nombrePros.toDouble)
    val html =
      s"""<span style="display:block;width:${r}px;height:${r}px;border-radius:50%;""" +
        s"""background:$color;opacity:.85;border:1px solid rgba(0,0,0,.35);box-sizing:border-box"></span>"""
    Leaflet.divIcon(
      js.Dynamic.literal(
        className = "hm-point",
        html      = html,
        iconSize  = js.Array(r, r),
        iconAnchor = js.Array(r / 2, r / 2)
      )
    )

  private def tooltip(p: MapPoint): String =
    val e       = LeafletMap.esc
    val exemples = p.professions.take(3).mkString(", ")
    s"""<strong>${e(p.commune)}</strong><br>""" +
      s"""CP ${e(p.codePostal)} · ${p.nombrePros} pros""" +
      (if exemples.nonEmpty then s"""<br><span style="color:#666">${e(exemples)}</span>""" else "")

  /** Cadre la vue sur les points de métropole. Les DOM (Réunion à ~55° de
    * longitude) écraseraient la France s'ils étaient inclus : on les ignore pour
    * le cadrage et on garde la vue France par défaut s'il ne reste rien.
    */
  private def fit(map: LMap, pts: List[MapPoint]): Unit =
    val metro = pts.filter(p => p.longitude >= -6 && p.longitude <= 10 && p.latitude >= 41 && p.latitude <= 52)
    metro match
      case Nil => () // conserve la vue France par défaut du fond
      case single :: Nil =>
        map.setView(js.Array(single.latitude, single.longitude), 9, js.Dynamic.literal(animate = false))
      case many =>
        val lats = many.map(_.latitude)
        val lons = many.map(_.longitude)
        map.fitBounds(
          js.Array(js.Array(lats.min, lons.min), js.Array(lats.max, lons.max)),
          js.Dynamic.literal(padding = js.Array(40, 40), maxZoom = 9, animate = false)
        )
