package fr.ipssi.healthmap.shared

import fr.ipssi.healthmap.shared.api.Endpoints
import fr.ipssi.healthmap.shared.model.*
import upickle.default.{read, write}

class CodecSuite extends munit.FunSuite:

  test("les modèles font l'aller-retour JSON") {
    val point = MapPoint("75001", "Paris", 48.8626, 2.3363, 706, List("Infirmier", "Médecin"))
    assertEquals(read[MapPoint](write(point)), point)

    val dept = DepartementCount("2A", "Corse-du-Sud", "Corse", 34)
    assertEquals(read[DepartementCount](write(dept)), dept)

    val densite = DensityStat("34", "Hérault", 2473, 1201883L, 205.8)
    assertEquals(read[DensityStat](write(densite)), densite)

    val chat = ChatResponse("Consultez un généraliste.", List("généraliste"), List("fièvre"), false)
    assertEquals(read[ChatResponse](write(chat)), chat)
  }

  test("le département de contexte est optionnel dans une question") {
    assertEquals(read[ChatRequest]("""{"message":"mal de dos"}"""), ChatRequest("mal de dos", None))
  }

  test("les URL d'API portent le filtre profession") {
    assertEquals(Endpoints.map(), "/api/map")
    assertEquals(Endpoints.map(List("Médecin")), "/api/map?professions=M%C3%A9decin")
    assertEquals(Endpoints.topCommunes(Nil, 5), "/api/top-communes?limit=5")
    assertEquals(Endpoints.topCommunes(List("Médecin"), 5), "/api/top-communes?professions=M%C3%A9decin&limit=5")
    assertEquals(Endpoints.coverage("2A"), "/api/coverage/2A")
    assertEquals(Endpoints.densityDepartements(), "/api/density/departements")
    assertEquals(Endpoints.densityRegions(List("Médecin")), "/api/density/regions?professions=M%C3%A9decin")
  }
