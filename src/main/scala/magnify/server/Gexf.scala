package magnify.server

import com.tinkerpop.blueprints.Direction._
import com.tinkerpop.blueprints.Graph
import com.tinkerpop.blueprints.Vertex
import scala.collection.JavaConversions._
import scala.xml.NodeSeq.seqToNodeSeq

object Gexf {
  implicit def apply(graph: Graph) = new Gexf(graph)
}

class Gexf (graph: Graph) {
  def toXml =
      <gexf xmlns="http://www.gexf.net/1.2draft" version="1.2">
        <meta lastmodifieddate="2009-03-20">
		  <creator>Magnify</creator>
		  <description>Graph</description>
		</meta>
		<graph mode="static" defaultedgetype="directed">
		  <nodes>{ nodes }</nodes>
		  <edges>{ edges }</edges>
	    </graph>
	  </gexf>

  def nodes: Iterable[xml.Node] =
    graph.getVertices.map { v =>
      <node id={v.getId.toString}/>
    }

  def edges: Iterable[xml.Node] =
    graph.getEdges.map { e =>
      val id = e.getId.toString
      val source = e.getVertex(IN).getId.toString
      val target = e.getVertex(OUT).getId.toString
      <edge id={id} source={source} target={target}/>
    }
}
