package controllers

import magnify.features.Sources
import magnify.features.SoftwareGraph._
import magnify.modules._
import play.api.mvc._
import scala.Predef._
import play.api.libs.json.Json._
import scala.Some

/**
 * @author Rafal Matusiak (rafal.matusiak@gmail.com)
 */
object DownloadRefactorings extends DownloadRefactorings(inject[Sources])

sealed class DownloadRefactorings (override protected val sources: Sources)
  extends Controller with ProjectList {

  def download(name: String, version: Int = -1) = Action { implicit request =>
    sources.get(name, version) match {
      case Some(graph) =>
        Ok(toJson(Map("move-class" -> movedClassesToMap(graph.movedClasses()))))
      case None =>
        NotFound
    }
  }

  private def movedClassesToMap(movedClasses: Iterable[(String, String, String)]): Seq[Map[String, String]] =
    for ((name, fromPackage, toPackage) <- movedClasses.toSeq) yield
      Map("name" -> name, "fromPackage" -> fromPackage, "toPackage" -> toPackage)
}
