package fr.ipssi.healthmap.server.geo

import cats.effect.IO
import cats.syntax.all.*
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.file.{Files, Path}
import fr.ipssi.healthmap.shared.api.Endpoints

/** Proxy et cache local des fonds GeoJSON.
  *
  * L'application Python rejouait un `requests.get` vers GitHub à chaque interaction ;
  * ici chaque fond n'est téléchargé qu'une fois, écrit sur disque, puis servi en local —
  * ce qui supprime aussi le blocage CORS côté navigateur.
  */
class GeoJsonCache(dir: Path, baseUrl: String):

  private val client = HttpClient.newHttpClient()

  /** Contenu d'un fond GeoJSON, `None` si le nom n'est pas au référentiel. */
  def get(name: String): IO[Option[String]] =
    if !Endpoints.geoJsonNames.contains(name) then IO.pure(None)
    else
      val file = dir.resolve(s"$name.geojson")
      IO.blocking(Files.exists(file)).flatMap {
        case true  => IO.blocking(Some(Files.readString(file)))
        case false => download(name).flatMap(body => IO.blocking(write(file, body)).as(Some(body)))
      }

  /** Télécharge les deux fonds au démarrage pour éviter la latence au premier affichage. */
  def prefetch: IO[Unit] =
    Endpoints.geoJsonNames.traverse_(get(_).void)

  private def download(name: String): IO[String] = IO.blocking {
    val request = HttpRequest.newBuilder(URI.create(s"$baseUrl/$name.geojson")).GET().build()
    val response = client.send(request, HttpResponse.BodyHandlers.ofString())
    if response.statusCode() != 200 then
      throw new RuntimeException(s"GeoJSON $name indisponible : HTTP ${response.statusCode()}")
    response.body()
  }

  private def write(file: Path, body: String): Unit =
    Files.createDirectories(file.getParent)
    Files.writeString(file, body)
    ()

object GeoJsonCache:

  val defaultBaseUrl = "https://raw.githubusercontent.com/gregoiredavid/france-geojson/master"

  def apply(dir: Path = Path.of("data", "geo")): GeoJsonCache =
    new GeoJsonCache(dir, defaultBaseUrl)
