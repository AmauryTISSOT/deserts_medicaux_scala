package fr.ipssi.healthmap.client.map

import com.raquo.laminar.api.L.*
import fr.ipssi.healthmap.shared.api.Endpoints
import fr.ipssi.healthmap.shared.model.DepartementCount

/** Choroplèthe départementale : jointure sur `properties.code` du GeoJSON des
  * départements, comme le `featureidkey="properties.code"` du Python. Alimentée
  * par un `Signal[List[DepartementCount]]`.
  */
object DepartementChoropleth:

  def apply(departements: Signal[List[DepartementCount]], heightPx: Int = 640): HtmlElement =
    Choropleth.build[DepartementCount](
      data = departements,
      geoJsonUrl = Endpoints.geoJson("departements"),
      joinKey = feature => Choropleth.prop(feature, "code"),
      keyOf = _.code,
      countOf = _.nombrePros,
      legendTitle = "Pros par département",
      tooltip = (feature, count) =>
        val e    = LeafletMap.esc
        val nom  = Choropleth.prop(feature, "nom")
        val code = Choropleth.prop(feature, "code")
        s"""<strong>${e(nom)} ($code)</strong><br>$count pros""",
      heightPx = heightPx
    )
