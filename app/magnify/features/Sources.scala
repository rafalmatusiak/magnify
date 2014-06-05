package magnify.features

import magnify.model.{Json, Archive}
import magnify.model.graph.Graph
import java.io.File

/**
 * @author Cezary Bartoszuk (cezarybartoszuk@gmail.com)
 * @author Rafal Matusiak (rafal.matusiak@gmail.com)
 */
trait Sources {

  def add(name: String, file: Archive): Graph

  def add(name: String, version: Int = -1): Graph

  def add(name: String, graph: Json): Json

  def remove(name: String, version: Int = -1)

  def list: Seq[String]

  def versions(name: String): Option[Iterable[Int]]

  def get(name: String, version: Int = -1): Option[Graph]

  def getJson(name: String): Option[Json]

  def addRuntime(name: String, file: File)
}
