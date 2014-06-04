package controllers

import magnify.features.Sources
import magnify.features.SoftwareGraph._
import magnify.modules._
import play.api.mvc._
import scala.Predef._
import scala.Some

/**
 * @author Rafal Matusiak (rafal.matusiak@gmail.com)
 */
object OptimizeGraph extends OptimizeGraph(inject[Sources])

sealed class OptimizeGraph (override protected val sources: Sources)
  extends Controller with ProjectList {

  def optimize(name: String, iterations: Int, tolerance: Int) = Action { implicit request =>
    sources.get(name) match {
      case Some(graph) =>
        graph.optimizeBySwappingClassesBetweenPackages(iterations, tolerance)
        Ok
      case None =>
        NotFound
    }
  }
}
