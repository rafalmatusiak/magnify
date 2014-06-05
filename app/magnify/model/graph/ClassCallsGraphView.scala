package magnify.model.graph

import com.tinkerpop.blueprints.{Edge, Vertex}
import com.tinkerpop.gremlin.pipes.filter.{LabelFilterPipe, PropertyFilterPipe}
import com.tinkerpop.pipes.filter.FilterPipe.Filter
import scala.collection.JavaConversions._
import com.tinkerpop.pipes.filter.OrFilterPipe

/**
 * @author Rafal Matusiak (rafal.matusiak@gmail.com)
 */
final class ClassCallsGraphView(graph: Graph) extends GraphView {

  override def vertices: Iterable[Vertex] =
    graph.vertices
      .add(classes)
      .toList

  private val classes =
    new PropertyFilterPipe[Vertex, String]("kind", "class", Filter.EQUAL)

  override def edges: Iterable[Edge] =
    graph.edges
      .add(calls)
      .toList

  private val calls =
    new OrFilterPipe[Edge](
      new LabelFilterPipe("runtime-calls", Filter.EQUAL),
      new LabelFilterPipe("calls", Filter.EQUAL))
}