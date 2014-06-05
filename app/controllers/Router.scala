package controllers

import play.api._
import play.api.mvc._

/**
 * @author Rafal Matusiak (rafal.matusiak@gmail.com)
 */
object Router extends Controller {
  // -- Javascript routing

  def javascriptRoutes = Action { implicit request =>
    import routes.javascript._
    Ok(
      Routes.javascriptRouter("jsRoutes")(
        ShowGraph.versionsJson,
        ShowGraph.showCustomJson,
        ShowGraph.showCustomPackagesJson,
        ShowGraph.showCustomClassesJson,
        OptimizeGraph.optimize
      )
    ).as("text/javascript")
  }
}
