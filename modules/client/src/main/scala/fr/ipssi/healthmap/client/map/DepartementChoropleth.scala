package fr.ipssi.healthmap.client.map

import com.raquo.laminar.api.L.*
import fr.ipssi.healthmap.shared.api.Endpoints

/** Choroplèthe départementale : jointure sur `properties.code` du GeoJSON des
  * départements, comme le `featureidkey="properties.code"` du Python. Alimentée
  * par une `Signal[Choropleth.View]` — effectifs bruts ou densité selon la
  * bascule.
  */
object DepartementChoropleth:

  def apply(view: Signal[Choropleth.View], heightPx: Int = 640): HtmlElement =
    Choropleth.build(
      view = view,
      geoJsonUrl = Endpoints.geoJson("departements"),
      joinKey = feature => Choropleth.prop(feature, "code"),
      heightPx = heightPx
    )
