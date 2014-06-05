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

  def optimize(name: String, iterations: Int, tolerance: Int, incremental: Boolean = false) = Action { implicit request =>
    sources.get(name, if (incremental) -1 else 0) match {
      case Some(graph) =>
        if (!incremental)
          sources.versions(name).getOrElse(Seq()).filter(_ != 0).foreach(sources.remove(name, _))
        val optimizedGraph = sources.add(name, if (incremental) -1 else 1)
        graph.copy(optimizedGraph)
        optimizedGraph.optimizeBySwappingClassesBetweenPackages(iterations, tolerance)
        Ok
      case None =>
        NotFound
    }
  }
}
