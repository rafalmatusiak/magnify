package magnify.model.graph

import com.tinkerpop.blueprints.{Edge, Vertex}
import com.tinkerpop.gremlin.pipes.filter.{LabelFilterPipe, PropertyFilterPipe}
import com.tinkerpop.pipes.filter.FilterPipe.Filter
import com.tinkerpop.pipes.filter.OrFilterPipe
import scala.collection.JavaConversions._

/**
 * @author Cezary Bartoszuk (cezary@codilime.com)
 */
final class PackagesWithImportsGraphView(graph: Graph) extends GraphView {

  override def vertices: Iterable[Vertex] =
    graph.vertices
        .add(packages)
        .toList

  private val packages =
    new PropertyFilterPipe[Vertex, String]("kind", "package", Filter.EQUAL)

  override def edges: Iterable[Edge] =
    graph.edges
        .add(inPackageOrImports)
        .toList

  private val inPackageOrImports =
    new OrFilterPipe[Edge](
      new LabelFilterPipe("in-package", Filter.EQUAL),
      new LabelFilterPipe("package-imports", Filter.EQUAL))
}