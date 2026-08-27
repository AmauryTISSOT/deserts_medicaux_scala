package fr.ipssi.healthmap.client.map

import com.raquo.laminar.api.L.*
import fr.ipssi.healthmap.shared.api.Endpoints

/** Choroplèthe régionale : jointure sur `properties.nom` du GeoJSON des régions,
  * comme le `featureidkey="properties.nom"` du Python. Alimentée par une
  * `Signal[Choropleth.View]` — effectifs bruts ou densité selon la bascule.
  */
object RegionChoropleth:

  def apply(view: Signal[Choropleth.View], heightPx: Int = 640): HtmlElement =
    Choropleth.build(
      view = view,
      geoJsonUrl = Endpoints.geoJson("regions"),
      joinKey = feature => Choropleth.prop(feature, "nom"),
      heightPx = heightPx
    )
