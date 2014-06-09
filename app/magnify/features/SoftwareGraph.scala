package magnify.features

import magnify.model.graph.Graph
import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.annotation.tailrec
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
      updatePackageImports()
      updatePackageCalls()
      updatePackageRuntimeCalls()
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
      updateCalculated()
    }

    private def updatePackageImports() {
      liftToPackage("imports")
    }

    private def updatePackageCalls() {
      liftToPackage("calls")
    }

    private def updatePackageRuntimeCalls() {
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
      val liftedRelation = "package-" + relation

      graph.removeEdges(liftedRelation)

      val edgesByRelatedPackages =
        (for {
          edge <- graph.edges
            .add(new LabelFilterPipe(relation, Filter.EQUAL))
            .toList
        } yield (containingPackage(edge.getVertex(Direction.OUT)), containingPackage(edge.getVertex(Direction.IN)), edge))
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

    def packageCallCount(): Int = {
      val calls = new LabelFilterPipe("package-calls", Filter.EQUAL)
      graph.edges.add(calls).toList.map(e => {
        if (e.getVertex(Direction.OUT) != e.getVertex(Direction.IN))
          e.getProperty("count").asInstanceOf[Int]
        else
          0
      }).sum
    }

    def optimizeBySwappingClassesBetweenPackages(iterations: Int, tolerance: Int) {
      @tailrec
      def randomFind[A](xs: List[A], p: (A) ⇒ Boolean): Option[A] =
        if (xs.isEmpty)
          None
        else
          xs.splitAt(scala.util.Random.nextInt(xs.length)) match {
            case (pre, e :: post)  => if (p(e)) Some(e) else randomFind(pre ::: post, p)
            case _ => None
          }

      def canSwapPackages(call :Edge): Boolean = {
        val c1 = call.getVertex(Direction.OUT)
        val c2 = call.getVertex(Direction.IN)

        val c1Pkg = new GremlinPipeline()
          .start(c1)
          .out("in-package")
          .toList

        val c2Pkg = new GremlinPipeline()
          .start(c2)
          .out("in-package")
          .toList

        if (c1Pkg.intersect(c2Pkg).nonEmpty)
          false
        else {
          val c1CallsPkg = new GremlinPipeline()
            .start(c1)
            .both("calls")
            .hasNot("name", name(c1))
            .hasNot("name", name(c2))
            .out("in-package")
            .toList
          val c2CallsPkg = new GremlinPipeline()
            .start(c2)
            .both("calls")
            .hasNot("name", name(c1))
            .hasNot("name", name(c2))
            .out("in-package")
            .toList

          val c1CallsC1Pkg = c1CallsPkg.count(c1Pkg.contains(_))
          val c1CallsC2Pkg = c1CallsPkg.count(c2Pkg.contains(_))
          val c2CallsC1Pkg = c2CallsPkg.count(c1Pkg.contains(_))
          val c2CallsC2Pkg = c2CallsPkg.count(c2Pkg.contains(_))

          c1CallsC1Pkg - c1CallsC2Pkg + c2CallsC2Pkg - c2CallsC1Pkg < tolerance
        }
      }

      @tailrec
      def optimizeBySwappingClassesBetweenPackages(i: Int, calls: List[Edge]) {
        if (i >= iterations)
          return
        else
          randomFind(calls, canSwapPackages) match {
            case Some(call) if i < iterations =>
              val c1 = call.getVertex(Direction.OUT)
              val c2 = call.getVertex(Direction.IN)
              swapPackages(c1, c2)
              optimizeBySwappingClassesBetweenPackages(i + 1, calls diff List(call))
            case _ =>
          }
      }

      val calls = graph.edges
        .add(new LabelFilterPipe("calls", Filter.EQUAL))
        .toList.toList

      logger.debug("iterations=" + iterations + " tolerance=" + tolerance)

      optimizeBySwappingClassesBetweenPackages(0, calls)
      updateCalculated()
    }

    def moveClassesRandomly() {
      val classes = graph.vertices
        .has("kind", "class")
        .asInstanceOf[GremlinPipeline[Vertex, Vertex]]
        .toList
      val packages = graph.vertices
        .has("kind", "package")
        .asInstanceOf[GremlinPipeline[Vertex, Vertex]]
        .toList
      for (cls <- classes) {
        moveToPackage(cls, packages(scala.util.Random.nextInt(packages.length)))
      }
      updateCalculated()
    }

    def movedClasses(): Iterable[(String, String, String)] = {
      // the class name is not changed on moving to another package
      // so a class was moved if its name does not equal
      // to N.x where
      // N - the name of the containing package
      // x - the simple name of the class
      for {
        cls <- graph.vertices
          .has("kind", "class")
          .asInstanceOf[GremlinPipeline[Vertex, Vertex]]
          .toList
        clsName = name(cls)
        inPkg <- containingPackage(cls)
        if pkgName(clsName) != name(inPkg)
      } yield (simpleName(clsName), pkgName(clsName), name(inPkg))
    }

    private def moveToPackage(v: Vertex, pkg: Vertex) {
      logger.debug("move: "+ name(v) + " to package " + name(pkg))
      graph.removeEdges(Seq(v), Direction.OUT,"in-package")
      graph.addEdge(v, "in-package", pkg)
    }

    private def swapPackages(v1: Vertex, v2: Vertex) {
      for {
        pkg2 <- new GremlinPipeline()
          .start(v2)
          .out("in-package")
          .asInstanceOf[GremlinPipeline[Vertex, Vertex]]
          .toList
        pkg1 <- new GremlinPipeline()
          .start(v1)
          .out("in-package")
          .asInstanceOf[GremlinPipeline[Vertex, Vertex]]
          .toList
      } {
        moveToPackage(v1, pkg2)
        moveToPackage(v2, pkg1)
      }
    }

    private def containingPackage(v: Vertex) = new GremlinPipeline()
      .start(v)
      .out("in-package")
      .toList

    private def simpleName(name: String): String =
      if (name.contains('.')) {
        name.substring(name.lastIndexOf('.')+1, name.length)
      } else {
        name
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
