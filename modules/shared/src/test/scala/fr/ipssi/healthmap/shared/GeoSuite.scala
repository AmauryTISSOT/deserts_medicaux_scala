package fr.ipssi.healthmap.shared

import fr.ipssi.healthmap.shared.ref.Geo

class GeoSuite extends munit.FunSuite:

  test("la Corse est éclatée en 2A et 2B") {
    assertEquals(Geo.departementFromCodePostal("20000"), Some("2A"))
    assertEquals(Geo.departementFromCodePostal("20190"), Some("2A"))
    assertEquals(Geo.departementFromCodePostal("20200"), Some("2B"))
    assertEquals(Geo.departementFromCodePostal("20600"), Some("2B"))
    assertEquals(Geo.regionFromCodePostal("20000"), "Corse")
  }

  test("les DOM sont sur trois chiffres") {
    assertEquals(Geo.departementFromCodePostal("97400"), Some("974"))
    assertEquals(Geo.regionFromCodePostal("97400"), "La Réunion")
  }

  test("les codes postaux invalides ne produisent pas de département") {
    assertEquals(Geo.departementFromCodePostal("7501"), None)
    assertEquals(Geo.departementFromCodePostal("2A000"), None)
    assertEquals(Geo.regionFromCodePostal(""), Geo.UNKNOWN)
  }

  test("le département 79 est les Deux-Sèvres et le 58 la Nièvre") {
    assertEquals(Geo.departementName("79"), "Deux-Sèvres")
    assertEquals(Geo.departementName("58"), "Nièvre")
    assertEquals(Geo.regionOf("79"), "Nouvelle-Aquitaine")
  }

  test("chaque département nommé appartient à une région") {
    val orphelins = Geo.deptNames.keySet.filterNot(Geo.deptToRegion.contains)
    assertEquals(orphelins, Set.empty[String])
  }

  test("aucun département n'est rattaché à deux régions") {
    assertEquals(Geo.deptToRegion.size, Geo.deptNames.size)
  }
