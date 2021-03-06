package magnify.model.graph

import com.tinkerpop.blueprints.{Edge, Vertex}
import scala.collection.JavaConversions._
import com.tinkerpop.gremlin.pipes.filter.{LabelFilterPipe, PropertyFilterPipe}
import com.tinkerpop.pipes.filter.FilterPipe.Filter
import com.tinkerpop.pipes.filter.OrFilterPipe

/**
 * @author Cezary Bartoszuk (cezary@codilime.com)
 * @author Rafal Matusiak (rafal.matusiak@gmail.com)
 */
final class CustomGraphView(graph: Graph) extends GraphView {

  override def vertices: Iterable[Vertex] =
      graph.vertices
        .add(classesAndPackages)
        .toList

  private val classesAndPackages = new OrFilterPipe[Vertex](
      new PropertyFilterPipe[Vertex, String]("kind", "package", Filter.EQUAL),
      new PropertyFilterPipe[Vertex, String]("kind", "class", Filter.EQUAL))

  override def edges: Iterable[Edge] =
      graph.edges
        .add(callsAndImports)
        .toList

  private val callsAndImports =
    new OrFilterPipe[Edge](
      new LabelFilterPipe("in-package", Filter.EQUAL),
      new LabelFilterPipe("package-imports", Filter.EQUAL),
      new LabelFilterPipe("package-calls", Filter.EQUAL),
      new LabelFilterPipe("package-runtime-calls", Filter.EQUAL),
      new LabelFilterPipe("imports", Filter.EQUAL),
      new LabelFilterPipe("calls", Filter.EQUAL),
      new LabelFilterPipe("runtime-calls", Filter.EQUAL))
}
