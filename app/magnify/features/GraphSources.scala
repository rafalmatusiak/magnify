package magnify.features

import java.io._
import magnify.model.graph.Graph
import magnify.model._
import scala.collection.mutable
import scala.io.Source
import scala.util.matching.Regex
import magnify.model.Ast
import magnify.features.SoftwareGraph._

/**
 * @author Cezary Bartoszuk (cezarybartoszuk@gmail.com)
 * @author Rafal Matusiak (rafal.matusiak@gmail.com)
 */
private[features] final class GraphSources (parse: Parser, imports: Imports) extends Sources {
  private val graphs = mutable.Map[String, Graph]()
  private val importedGraphs = mutable.Map[String, Json]()

  override def add(name: String, file: Archive) {
    val graph = Graph.tinker
    graph.process(classesFrom(file), imports)
    graphs += name -> graph
  }

  override def add(name: String, graph: Json) {
    importedGraphs += name -> graph
  }

  private def classesFrom(file: Archive): Seq[(Ast, String)] = parse(file.extract {
      (name, content) =>
        if (isJavaFile(name) ) {
          val stringContent = inputStreamToString(content)
          Seq((name, new ByteArrayInputStream(stringContent.getBytes("UTF-8"))))
        } else {
          Seq()
        }
    })

  private def inputStreamToString(is: InputStream) = {
    val rd: BufferedReader = new BufferedReader(new InputStreamReader(is, "UTF-8"))
    val builder = new StringBuilder()
    try {
      var line = rd.readLine
      while (line != null) {
        builder.append(line + "\n")
        line = rd.readLine
      }
    } finally {
      rd.close()
    }
    builder.toString()
  }

  private def isJavaFile(name: String): Boolean =
    name.endsWith(".java") && !name.endsWith("Test.java")

  override def list: Seq[String] =
    graphs.keys.toSeq ++ importedGraphs.keys.toSeq

  override def get(name: String): Option[Graph] =
    graphs.get(name)

  override def getJson(name: String) =
    importedGraphs.get(name)

  private object CsvCall extends Regex("""([^;]+);([^;]+);(\d+)""", "from", "to", "count")

  private object ClassFromCall extends Regex("""(.* |^)([^ ]*)\.[^.(]+\(.*""")

  def addRuntime(name: String, file: File) {
    for (graph <- get(name)) {
      val runtime = for {
        CsvCall(from, to, count) <- Source.fromFile(file).getLines().toSeq
        ClassFromCall(_, fromClass) = from
        ClassFromCall(_, toClass) = to
      } yield (fromClass, toClass, count.toInt)
      graph.addRuntimeCalls(runtime)
    }
  }
}
