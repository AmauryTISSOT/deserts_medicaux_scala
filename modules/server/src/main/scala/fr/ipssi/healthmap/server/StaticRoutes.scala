package fr.ipssi.healthmap.server

import cats.effect.IO
import fs2.io.file.Path
import org.http4s.*
import org.http4s.dsl.io.*

/** Service des fichiers statiques : `static/index.html`, la feuille de style et le
  * bundle Scala.js déposé par `client/fastLinkJS` dans `static/js`.
  */
object StaticRoutes:

  private val root = Path("static")

  def apply(): HttpRoutes[IO] = HttpRoutes.of[IO] {

    case request @ GET -> Root =>
      serve(root / "index.html", request)

    case request @ GET -> path if isAsset(path) =>
      serve(path.segments.foldLeft(root)((p, s) => p / s.decoded()), request)
  }

  /** Un chemin d'asset ne contient ni `..` ni segment vide : la traversée de
    * répertoire est refusée avant même d'atteindre le disque.
    */
  private def isAsset(path: Uri.Path): Boolean =
    path.segments.nonEmpty && path.segments.forall(s =>
      s.decoded() != ".." && s.decoded() != "." && s.decoded().nonEmpty
    )

  private def serve(file: Path, request: Request[IO]): IO[Response[IO]] =
    StaticFile.fromPath(file, Some(request)).getOrElseF(NotFound())
