package fr.ipssi.healthmap.client.map

import com.raquo.laminar.api.L.*
import com.raquo.laminar.lifecycle.MountContext
import fr.ipssi.healthmap.client.facade.*
import org.scalajs.dom
import scala.scalajs.js

/** Briques communes aux trois composants de carte : fond OpenStreetMap, groupe
  * avec clustering, légende, et surtout le conteneur Laminar qui gère le cycle
  * de vie d'une carte Leaflet (création au montage, `remove()` au démontage).
  *
  * Le pont entre Laminar (déclaratif) et Leaflet (impératif) est isolé ici pour
  * que les composants ne manipulent que des modèles et des `Signal`.
  */
object LeafletMap:

  /** Centre approximatif de la France métropolitaine, vue par défaut. */
  val franceCenter: js.Array[Double] = js.Array(46.6, 2.4)

  /** Carte de base avec fond de tuiles OpenStreetMap. */
  def baseMap(element: dom.Element, center: js.Array[Double], zoom: Double): LMap =
    val map = Leaflet.map(
      element,
      js.Dynamic.literal(center = center, zoom = zoom)
    )
    Leaflet
      .tileLayer(
        "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png",
        js.Dynamic.literal(
          maxZoom = 19,
          attribution = "&copy; <a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap</a>"
        )
      )
      .addTo(map)
    map

  /** Groupe de marqueurs : avec clustering si le greffon markercluster est
    * chargé, sinon simple groupe — les ~6 000 points s'affichent quand même.
    */
  def markerGroup(): LayerGroup =
    val l = Leaflet.asInstanceOf[js.Dynamic]
    if js.typeOf(l.markerClusterGroup) == "function" then
      l.markerClusterGroup(
        js.Dynamic.literal(chunkedLoading = true, maxClusterRadius = 50, spiderfyOnMaxZoom = true)
      ).asInstanceOf[LayerGroup]
    else Leaflet.layerGroup()

  /** Contrôle de légende positionné en bas à droite, construit depuis un modèle
    * `(libellé, couleur)`. Le fond clair reste lisible sur la carte quel que
    * soit le thème du reste de la page.
    */
  def legendControl(title: String, entries: List[(String, String)]): Control =
    val ctrl = Leaflet.control(js.Dynamic.literal(position = "bottomright"))
    ctrl.onAdd = { (_: LMap) =>
      val box = Leaflet.DomUtil.create("div", "hm-legend")
      box.setAttribute(
        "style",
        "background:rgba(255,255,255,.92);color:#1a1a19;padding:8px 10px;" +
          "border-radius:8px;font:12px/1.4 system-ui,-apple-system,sans-serif;" +
          "box-shadow:0 1px 4px rgba(0,0,0,.25);max-width:180px"
      )
      val rows = entries
        .map { (label, color) =>
          s"""<div style="display:flex;align-items:center;gap:6px;margin:2px 0">""" +
            s"""<span style="flex:0 0 14px;width:14px;height:14px;border-radius:3px;""" +
            s"""background:$color;border:1px solid rgba(0,0,0,.25)"></span>""" +
            s"""<span>${esc(label)}</span></div>"""
        }
        .mkString
      box.innerHTML = s"""<div style="font-weight:600;margin-bottom:4px">${esc(title)}</div>$rows"""
      box
    }
    ctrl

  /** Conteneur Laminar hébergeant une carte Leaflet.
    *
    * `onReady` reçoit la carte initialisée et le contexte de montage : le
    * composant y branche son abonnement aux données via `ctx.owner`. Le
    * `statusSignal` optionnel affiche un bandeau (« Chargement… », erreur).
    */
  def container(
      heightPx: Int = 640,
      center: js.Array[Double] = franceCenter,
      zoom: Double = 5,
      statusSignal: Signal[Option[String]] = Signal.fromValue(None)
  )(onReady: (LMap, MountContext[HtmlElement]) => Unit): HtmlElement =
    div(
      cls := "hm-map-wrap",
      position := "relative",
      width := "100%",
      height := s"${heightPx}px",
      borderRadius := "10px",
      overflow := "hidden",
      div(
        width := "100%",
        height := "100%",
        onMountUnmountCallbackWithState[HtmlElement, LMap](
          mount = { ctx =>
            val map = baseMap(ctx.thisNode.ref, center, zoom)
            onReady(map, ctx)
            js.timers.setTimeout(150) { map.invalidateSize() }
            map
          },
          unmount = { (_, maybeMap) => maybeMap.foreach(_.remove()) }
        )
      ),
      child.maybe <-- statusSignal.map(_.map(overlay))
    )

  private def overlay(message: String): HtmlElement =
    div(
      position := "absolute",
      top := "10px",
      left := "10px",
      zIndex := "1000",
      padding := "6px 12px",
      borderRadius := "8px",
      backgroundColor := "rgba(0,0,0,.72)",
      color := "#fff",
      fontSize := "13px",
      message
    )

  /** Échappement HTML minimal pour les libellés injectés dans les infobulles. */
  def esc(s: String): String =
    s.replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
