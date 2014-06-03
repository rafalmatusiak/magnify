package magnify.features

import magnify.model.graph.Graph
import scala.collection.JavaConversions._
import scala.collection.mutable
import com.tinkerpop.blueprints.{Edge, Vertex, Direction}
import com.tinkerpop.gremlin.java.GremlinPipeline
import edu.uci.ics.jung.algorithms.scoring.PageRank
import com.tinkerpop.blueprints.oupls.jung.GraphJung
import magnify.model.Ast
import com.tinkerpop.gremlin.pipes.filter.LabelFilterPipe
import com.tinkerpop.pipes.filter.FilterPipe.Filter
import play.api.Logger

/**
 * @author Cezary Bartoszuk (cezarybartoszuk@gmail.com)
 * @author Rafal Matusiak (rafal.matusiak@gmail.com)
 */
object SoftwareGraph {

  private val logger = Logger(getClass.getSimpleName)

  implicit class GraphOps(val graph: Graph) extends AnyVal {

    def process(classes: Iterable[(Ast, String)], imports: Imports) {
      build(classes, imports)
      updateCalculated()
    }

    private def build(classes: Iterable[(Ast, String)], imports: Imports) {
      addClasses(classes)
      addImports(classes.map(_._1), imports)
      addCalls(classes.map(_._1))
      addPackages()
    }

    private def updateCalculated() {
      calculateTransitiveClosure()
      calculateMetrics()
    }

    private def calculateTransitiveClosure() {
      liftToPackage("imports")
      liftToPackage("calls")
    }

    private def calculateMetrics() {
      computeLinesOfCode()
      addPageRank()
    }

    private def addClasses(classes: Iterable[(Ast, String)]) {
      for ((ast, source) <- classes) {
        logger.debug("class: " + ast.className)
        val vertex = graph.addVertex
        vertex.setProperty("kind", "class")
        vertex.setProperty("name", ast.className)
        vertex.setProperty("source-code", source)
      }
    }

    private def addImports(classes: Iterable[Ast], imports: Imports) {
      for {
        (outCls, imported) <- imports.resolve(classes)
        inCls <- imported
      } for {
        inVertex <- classesNamed(inCls)
        outVertex <- classesNamed(outCls)
      } {
        logger.debug(name(outVertex) + " -imports-> " + name(inVertex))
        graph.addEdge(outVertex, "imports", inVertex)
      }
    }

    private def addPackages() {
      def classes = graph.vertices.has("kind", "class").toList.toIterable.asInstanceOf[Iterable[Vertex]]
      val packageNames = packagesFrom(classes)
      val packageByName = addPackageVertices(packageNames)
      addPackageEdges(packageByName)
      addClassPackageEdges(classes, packageByName)
    }

    private def packagesFrom(classes: Iterable[Vertex]): Set[String] =
      (for {
        cls <- classes
        clsName = name(cls)
        pkgName <- clsName.split('.').inits.toList.tail.map(_.mkString("."))
      } yield pkgName).toSet

    private def addPackageVertices(packageNames: Set[String]): Map[String, Vertex] =
      (for (pkgName <- packageNames) yield {
        logger.debug("package: " + pkgName)
        val pkg = graph.addVertex
        pkg.setProperty("kind", "package")
        pkg.setProperty("name", pkgName)
        pkgName -> pkg
      }).toMap

    private def addPackageEdges(packageByName: Map[String, Vertex]) {
      for ((packageName, pkg) <- packageByName; if packageName.nonEmpty) {
        val outer = packageByName(pkgName(packageName))
        logger.debug(name(pkg) + " -in-package-> " + name(outer))
        graph.addEdge(pkg, "in-package", outer)
      }
    }

    private def addClassPackageEdges(classes: Iterable[Vertex], packageByName: Map[String, Vertex]) {
      for (cls <- classes) {
        val pkg = packageByName(pkgName(name(cls)))
        logger.debug(name(cls) + " in " + name(pkg))
        graph.addEdge(cls, "in-package", pkg)
      }
    }

    private def addCalls(classes: Iterable[Ast]) {
      for {
        cls <- classes
        outCls = cls.className
        (inCls, count) <- cls.calls.groupBy(x => x).mapValues(_.length)
      } for {
        inVertex <- classesNamed(inCls)
        outVertex <- classesNamed(outCls)
      } {
        logger.debug(name(outVertex) + " -calls(" + count + ")-> " + name(inVertex))
        val edge = graph.addEdge(outVertex, "calls", inVertex)
        edge.setProperty("count", count)
      }
    }

    def addRuntimeCalls(runtime: Iterable[(String, String, Int)]) {
      val calls = runtime.groupBy {case (a, b, _) => (a, b)}.mapValues(s => s.map(_._3).sum)
      for {
        ((fromClass, toClass), count) <- calls
        from <- classesNamed(fromClass)
        to <- classesNamed(toClass)
      } {
        logger.debug(name(from) + " -runtime-calls(" + count + ")-> " + name(to))
        val e = graph.addEdge(from.asInstanceOf[Vertex], "runtime-calls", to.asInstanceOf[Vertex])
        e.setProperty("count", count)
      }
      liftToPackage("runtime-calls")
    }

    /*
    private def liftToPackage(packages: Iterable[Vertex], relation: String) {
      for {
        pkg <- graph.vertices
          .has("kind", "class")
          .out("in-package")
          .asInstanceOf[GremlinPipeline[Vertex, Vertex]]
          .toList.toSet[Vertex]
        (relatedPkg: Vertex, count) <- new GremlinPipeline()
          .start(pkg)
          .in("in-package")
          .out(relation)
          .out("in-package")
          .toList
          .groupBy(v => v)
          .map(p => (p._1, p._2.length))
      } {
        logger.debug(name(pkg) + " -" + relation + "(" + count + ")-> " + name(relatedPkg))
        val e = graph.addEdge(pkg, "package-" + relation, relatedPkg)
        e.setProperty("count", count)
      }
    }
    */

    private def liftToPackage(relation: String) {
      def inPackage(v: Vertex) = new GremlinPipeline()
        .start(v)
        .out("in-package")
        .toList

      val liftedRelation = "package-" + relation

      val edgesByRelatedPackages =
        (for {
          edge <- graph.edges
            .add(new LabelFilterPipe(relation, Filter.EQUAL))
            .toList
        } yield (inPackage(edge.getVertex(Direction.OUT)), inPackage(edge.getVertex(Direction.IN)), edge))
        .groupBy {case (a, b, _) => (a, b)}.mapValues(s => s.map(_._3))

      for {
        ((from, to), edges) <- edgesByRelatedPackages
        pkg <- from
        relatedPkg <- to
      } {
        val count = edges.map(edge => {
          if (edge.getPropertyKeys.contains("count"))
            edge.getProperty("count").asInstanceOf[Int]
          else
            1
        }).sum
        logger.debug(name(pkg) + " -" + liftedRelation + "(" + count + ")-> " + name(relatedPkg))
        val e = graph.addEdge(pkg, liftedRelation, relatedPkg)
        e.setProperty("count", count)
      }
    }

    private def addPageRank() {
      val pageRank = new PageRank[Vertex, Edge](new GraphJung(graph.blueprintsGraph), 0.15)
      pageRank.evaluate()
      for (vertex <- graph.blueprintsGraph.getVertices) {
        vertex.setProperty("page-rank", pageRank.getVertexScore(vertex).toString)
      }
    }

    private def computeLinesOfCode() {
      graph
        .vertices
        .has("kind", "class")
        .toList foreach {
        case v: Vertex =>
          val linesOfCode = v.getProperty("source-code").toString.count(_ == '\n')
          v.setProperty("metric--lines-of-code", linesOfCode)
      }
      graph
        .vertices
        .has("kind", "package").toList foreach { case pkg: Vertex =>
        val elems = graph.vertices.has("name", pkg.getProperty("name"))
          .in("in-package")
          .has("kind", "class")
          .property("metric--lines-of-code")
          .toList.toSeq.asInstanceOf[mutable.Seq[Int]]
        val avg = elems.sum.toDouble / elems.size.toDouble
        pkg.setProperty("metric--lines-of-code", avg)
      }
    }

    private def pkgName(name: String): String =
      if (name.contains('.')) {
        name.substring(0, name.lastIndexOf('.'))
      } else {
        ""
      }

    private def name(cls: Vertex): String =
      cls.getProperty("name").toString

    private def classesNamed(name: String): Iterable[Vertex] =
      graph
        .vertices
        .has("kind", "class")
        .has("name", name)
        .asInstanceOf[GremlinPipeline[Vertex, Vertex]]
        .toList
  }
}
