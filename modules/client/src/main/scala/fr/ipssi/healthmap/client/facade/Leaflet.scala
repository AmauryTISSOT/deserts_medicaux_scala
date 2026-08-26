package fr.ipssi.healthmap.client.facade

import org.scalajs.dom
import scala.scalajs.js
import scala.scalajs.js.annotation.*

/** Façade Scala.js minimale vers Leaflet 1.9 et le greffon markercluster.
  *
  * Leaflet est chargé en global (`window.L`) par les balises `<script>` de
  * `static/index.html` — la configuration `ModuleKind.NoModule` du linker
  * impose ce mode plutôt qu'un `import` de module. On n'expose ici que la
  * surface utilisée par le lot D ; les options sont passées en `js.Any`
  * (typiquement `js.Dynamic.literal(...)`) pour ne pas figer tout le schéma
  * de Leaflet dans des traits.
  */
@js.native
@JSGlobal("L")
object Leaflet extends js.Object:

  def map(element: dom.Element, options: js.Any = js.native): LMap = js.native

  def tileLayer(urlTemplate: String, options: js.Any = js.native): TileLayer = js.native

  def marker(latLng: js.Array[Double], options: js.Any = js.native): Marker = js.native

  def divIcon(options: js.Any): DivIcon = js.native

  def geoJSON(data: js.Any = js.native, options: js.Any = js.native): GeoJSON = js.native

  def layerGroup(): LayerGroup = js.native

  def control(options: js.Any = js.native): Control = js.native

  /** Utilitaires DOM de Leaflet, utilisés pour construire la légende. */
  val DomUtil: DomUtil = js.native

/** Instance de carte. Les méthodes renvoyant `this` permettent le chaînage. */
@js.native
trait LMap extends js.Object:
  def setView(center: js.Array[Double], zoom: Double, options: js.Any = js.native): LMap = js.native
  def fitBounds(bounds: js.Any, options: js.Any = js.native): LMap = js.native
  def addLayer(layer: Layer): LMap    = js.native
  def removeLayer(layer: Layer): LMap = js.native
  def invalidateSize(animate: Boolean = js.native): LMap = js.native
  def remove(): Unit = js.native

/** Couche générique : tuiles, marqueur, groupe, GeoJSON… */
@js.native
trait Layer extends js.Object:
  def addTo(map: LMap): Layer = js.native
  def bindTooltip(content: String, options: js.Any = js.native): Layer = js.native
  def bindPopup(content: String, options: js.Any = js.native): Layer   = js.native

@js.native trait TileLayer extends Layer
@js.native trait Marker    extends Layer
@js.native trait DivIcon   extends js.Object

/** Groupe de couches ; `markerClusterGroup` en est un sous-type au clustering. */
@js.native
trait LayerGroup extends Layer:
  def addLayer(layer: Layer): LayerGroup = js.native
  def clearLayers(): LayerGroup          = js.native

/** Couche GeoJSON : `resetStyle` restaure le style d'une entité après survol. */
@js.native
trait GeoJSON extends Layer:
  def getBounds(): js.Any            = js.native
  def resetStyle(layer: js.Any): Unit = js.native

/** Contrôle positionné (ici la légende). `onAdd` est assignable : lui affecter
  * une fonction masque la méthode du prototype, ce qui est la façon idiomatique
  * de fabriquer un contrôle personnalisé sans `L.Control.extend`.
  */
@js.native
trait Control extends js.Object:
  var onAdd: js.Function1[LMap, dom.HTMLElement] = js.native
  def addTo(map: LMap): Control = js.native
  def remove(): Unit            = js.native

@js.native
trait DomUtil extends js.Object:
  def create(tagName: String, className: String = js.native): dom.HTMLElement = js.native
