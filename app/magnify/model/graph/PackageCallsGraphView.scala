package magnify.model.graph

import com.tinkerpop.blueprints.{Edge, Vertex}
import com.tinkerpop.gremlin.pipes.filter.{LabelFilterPipe, PropertyFilterPipe}
import com.tinkerpop.pipes.filter.FilterPipe.Filter
import scala.collection.JavaConversions._
import com.tinkerpop.pipes.filter.OrFilterPipe

/**
 * @author Rafal Matusiak (rafal.matusiak@gmail.com)
 */
final class PackageCallsGraphView(graph: Graph) extends GraphView {

  override def vertices: Iterable[Vertex] =
    graph.vertices
      .add(packages)
      .toList

  private val packages =
    new PropertyFilterPipe[Vertex, String]("kind", "package", Filter.EQUAL)

  override def edges: Iterable[Edge] =
    graph.edges
      .add(calls)
      .toList

  private val calls =
    new OrFilterPipe[Edge](
      new LabelFilterPipe("package-runtime-calls", Filter.EQUAL),
      new LabelFilterPipe("package-calls", Filter.EQUAL))
}