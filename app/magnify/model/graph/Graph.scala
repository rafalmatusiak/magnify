package magnify.model.graph

import com.tinkerpop.blueprints.impls.tg.TinkerGraph
import com.tinkerpop.blueprints.{Graph => BlueprintsGraph, _}
import com.tinkerpop.blueprints.util.ElementHelper
import com.tinkerpop.gremlin.java.GremlinPipeline
import com.tinkerpop.gremlin.pipes.filter.LabelFilterPipe
import com.tinkerpop.pipes.filter.FilterPipe.Filter
import scala.collection.JavaConversions._

/**
 * @author Cezary Bartoszuk (cezarybartoszuk@gmail.com)
 * @author Rafal Matusiak (rafal.matusiak@gmail.com)
 */
object Graph {
  implicit def gremlinPipelineAsScalaIterable[S, E](pipe: GremlinPipeline[S, E]): Iterable[E] =
    collectionAsScalaIterable(pipe.toList)

  def tinker: Graph =
    new Graph(new TinkerGraph)
}

final class Graph (val blueprintsGraph: BlueprintsGraph) {

  def vertices: GremlinPipeline[Vertex, Vertex] =
    new GremlinPipeline(blueprintsGraph.getVertices, true)

  def edges: GremlinPipeline[Edge, Edge] =
    new GremlinPipeline(blueprintsGraph.getEdges, true)

  def addVertex: Vertex =
    blueprintsGraph.addVertex(null)

  def removeVertex(vertex: Vertex) {
    blueprintsGraph.removeVertex(vertex)
  }

  def addEdge(from: Vertex, label: String, to: Vertex): Edge =
    blueprintsGraph.addEdge(null, from, to, label)

  def removeEdge(edge: Edge) {
    blueprintsGraph.removeEdge(edge)
  }

  def removeEdges(label: String) {
    for {
      edge <- edges
        .add(new LabelFilterPipe(label, Filter.EQUAL))
        .toList
    } {
      removeEdge(edge)
    }
  }

  def removeEdges(vertices: Iterable[Vertex], direction: Direction, labels: String*) {
    def edges(v: Vertex) =
      (if (direction == Direction.OUT)
        new GremlinPipeline()
          .start(v)
          .outE(labels:_*)
      else if (direction == Direction.IN)
        new GremlinPipeline()
          .start(v)
          .inE(labels:_*)
      else
        new GremlinPipeline()
          .start(v)
          .bothE(labels:_*))
        .asInstanceOf[GremlinPipeline[Edge, Edge]]
        .toList.toSet[Edge]
    for {
      v <- vertices
      edge <- edges(v)
    } {
      removeEdge(edge)
    }
  }

  def copy(toGraph: Graph) {
    import scala.collection.mutable
    val ids = mutable.Map[Object, Object]()
    for (vertex <- blueprintsGraph.getVertices) {
      val toVertex = toGraph.blueprintsGraph.addVertex(vertex.getId)
      ids += vertex.getId -> toVertex.getId
      ElementHelper.copyProperties(vertex, toVertex)
    }
    for (edge <- blueprintsGraph.getEdges) {
      val toEdge = toGraph.blueprintsGraph.addEdge(
        edge.getId,
        toGraph.blueprintsGraph.getVertex(ids(edge.getVertex(Direction.OUT).getId)),
        toGraph.blueprintsGraph.getVertex(ids(edge.getVertex(Direction.IN).getId)),
        edge.getLabel
      )
      ElementHelper.copyProperties(edge, toEdge)
    }
  }
}
