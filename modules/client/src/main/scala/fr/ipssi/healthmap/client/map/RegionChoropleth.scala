package fr.ipssi.healthmap.client.map

import com.raquo.laminar.api.L.*
import fr.ipssi.healthmap.shared.api.Endpoints
import fr.ipssi.healthmap.shared.model.RegionCount

/** Choroplèthe régionale : jointure sur `properties.nom` du GeoJSON des régions,
  * comme le `featureidkey="properties.nom"` du Python. Alimentée par un
  * `Signal[List[RegionCount]]`.
  */
object RegionChoropleth:

  def apply(regions: Signal[List[RegionCount]], heightPx: Int = 640): HtmlElement =
    Choropleth.build[RegionCount](
      data = regions,
      geoJsonUrl = Endpoints.geoJson("regions"),
      joinKey = feature => Choropleth.prop(feature, "nom"),
      keyOf = _.nom,
      countOf = _.nombrePros,
      legendTitle = "Pros par région",
      tooltip = (feature, count) =>
        val e = LeafletMap.esc
        s"""<strong>${e(Choropleth.prop(feature, "nom"))}</strong><br>$count pros""",
      heightPx = heightPx
    )
