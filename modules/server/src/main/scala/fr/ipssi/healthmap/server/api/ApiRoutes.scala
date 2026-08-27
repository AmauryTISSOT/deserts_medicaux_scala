package fr.ipssi.healthmap.server.api

import cats.effect.IO
import cats.syntax.all.*
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.headers.`Content-Type`
import upickle.default.{Writer, read, write}

import fr.ipssi.healthmap.server.chat.ChatService
import fr.ipssi.healthmap.server.data.ProfessionalSource
import fr.ipssi.healthmap.server.geo.GeoJsonCache
import fr.ipssi.healthmap.shared.model.*

/** Routes de l'API, sérialisées par les codecs du module `shared`.
  *
  * Le paramètre `professions` est une liste séparée par des virgules ; absent ou
  * vide, il signifie « toutes professions ».
  */
object ApiRoutes:

  private object ProfessionsParam extends OptionalQueryParamDecoderMatcher[String]("professions")
  private object LimitParam       extends OptionalQueryParamDecoderMatcher[Int]("limit")

  // Le charset doit être déclaré explicitement : sans lui, un client qui ne
  // suppose pas l'UTF-8 par défaut (ex. PowerShell) décode les accents du
  // corps JSON en Latin-1 et affiche des caractères corrompus ("Ã©" pour "é").
  private val json = `Content-Type`(MediaType.application.json, Charset.`UTF-8`)

  private def ok[A: Writer](a: A): IO[Response[IO]] =
    Ok(write(a)).map(_.withContentType(json))

  private def filter(param: Option[String]): Set[String] =
    param.toList.flatMap(_.split(',')).map(_.trim).filter(_.nonEmpty).toSet

  def apply(source: ProfessionalSource, chat: ChatService, geo: GeoJsonCache): HttpRoutes[IO] =
    HttpRoutes.of[IO] {

      case GET -> Root / "api" / "professions" =>
        ok(source.professions)

      case GET -> Root / "api" / "map" :? ProfessionsParam(p) =>
        ok(source.mapPoints(filter(p)))

      case GET -> Root / "api" / "regions" :? ProfessionsParam(p) =>
        ok(source.byRegion(filter(p)))

      case GET -> Root / "api" / "departements" :? ProfessionsParam(p) =>
        ok(source.byDepartement(filter(p)))

      case GET -> Root / "api" / "top-communes" :? ProfessionsParam(p) +& LimitParam(l) =>
        ok(source.topCommunes(filter(p), l.getOrElse(10).max(1).min(100)))

      case GET -> Root / "api" / "coverage" / code =>
        source.coverage(code) match
          case Some(report) => ok(report)
          case None         => NotFound(write(ApiError(s"Département inconnu : $code"))).map(_.withContentType(json))

      case request @ POST -> Root / "api" / "chat" =>
        request
          .as[String]
          .flatMap(body => IO(read[ChatRequest](body)))
          .flatMap(chat.respond)
          .flatMap(ok(_))
          .handleErrorWith(e =>
            BadRequest(write(ApiError(s"Requête invalide : ${e.getMessage}"))).map(_.withContentType(json))
          )

      case GET -> Root / "geo" / name if name.endsWith(".geojson") =>
        geo.get(name.stripSuffix(".geojson")).flatMap {
          case Some(body) => Ok(body).map(_.withContentType(json))
          case None       => NotFound(write(ApiError(s"Fond inconnu : $name"))).map(_.withContentType(json))
        }
    }
