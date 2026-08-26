package fr.ipssi.healthmap.server

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.http4s.*
import org.http4s.implicits.*
import upickle.default.read

import fr.ipssi.healthmap.server.api.ApiRoutes
import fr.ipssi.healthmap.server.chat.ChatService
import fr.ipssi.healthmap.server.data.InMemoryProfessionalSource
import fr.ipssi.healthmap.server.geo.GeoJsonCache
import fr.ipssi.healthmap.shared.model.*

class ApiRoutesSuite extends munit.FunSuite:

  private val source = InMemoryProfessionalSource.stub
  private val app =
    ApiRoutes(source, ChatService.rulesBased(source), GeoJsonCache()).orNotFound

  private def get(uri: String): Response[IO] =
    app.run(Request[IO](Method.GET, Uri.unsafeFromString(uri))).unsafeRunSync()

  private def body(response: Response[IO]): String =
    response.as[String].unsafeRunSync()

  test("GET /api/professions renvoie le référentiel trié par effectif") {
    val r = get("/api/professions")
    assertEquals(r.status, Status.Ok)
    val professions = read[List[Profession]](body(r))
    assertEquals(professions.head.nom, "Médecin")
    assertEquals(professions.map(_.effectif), professions.map(_.effectif).sorted.reverse)
  }

  test("le filtre profession réduit les agrégats") {
    val tous     = read[List[MapPoint]](body(get("/api/map")))
    val medecins = read[List[MapPoint]](body(get("/api/map?professions=M%C3%A9decin")))
    assert(medecins.map(_.nombrePros).sum < tous.map(_.nombrePros).sum)
    assert(medecins.forall(_.professions == List("Médecin")))
  }

  test("la Corse apparaît dans les agrégats départementaux") {
    val depts = read[List[DepartementCount]](body(get("/api/departements")))
    assertEquals(depts.filter(_.region == "Corse").map(_.code).sorted, List("2A", "2B"))
  }

  test("GET /api/regions couvre tout l'effectif") {
    val regions = read[List[RegionCount]](body(get("/api/regions")))
    assertEquals(regions.map(_.nombrePros).sum, source.total(Set.empty))
    assert(!regions.exists(_.nom == "Inconnue"))
  }

  test("GET /api/top-communes respecte la limite") {
    val top = read[List[CommuneCount]](body(get("/api/top-communes?limit=3")))
    assertEquals(top.size, 3)
    assertEquals(top.head.commune, "Paris")
  }

  test("GET /api/coverage répond 404 sur un département inconnu") {
    assertEquals(get("/api/coverage/2A").status, Status.Ok)
    assertEquals(get("/api/coverage/00").status, Status.NotFound)
  }

  test("POST /api/chat oriente et détecte l'urgence") {
    def ask(json: String): ChatResponse =
      val request = Request[IO](Method.POST, uri"/api/chat").withEntity(json)
      read[ChatResponse](body(app.run(request).unsafeRunSync()))

    val orientation = ask("""{"message":"j'ai un mal de dents","departement":"2A"}""")
    assertEquals(orientation.specialites, List("dentiste"))
    assert(orientation.reponse.contains("Corse-du-Sud"))
    assert(!orientation.urgence)

    assert(ask("""{"message":"je n'arrive plus à respirer"}""").urgence)
  }

  test("POST /api/chat rejette un corps invalide") {
    val request = Request[IO](Method.POST, uri"/api/chat").withEntity("pas du json")
    assertEquals(app.run(request).unsafeRunSync().status, Status.BadRequest)
  }

  test("un fond GeoJSON inconnu répond 404") {
    assertEquals(get("/geo/communes.geojson").status, Status.NotFound)
  }
