package magnify.services

import japa.parser.JavaParser
import japa.parser.ast.CompilationUnit
import japa.parser.ast.expr.{QualifiedNameExpr, NameExpr}
import java.io.InputStream
import magnify.features.Parser
import magnify.model.Ast
import scala.annotation.tailrec
import scala.collection.JavaConversions._


/**
 * @author Cezary Bartoszuk (cezarybartoszuk@gmail.com)
 */
private[services] final class ClassAndImportsParser extends Parser {
  JavaParser.setCacheParser(false)  // TODO: move this to dependency injection.

  override def apply(input: InputStream): Seq[Ast] = {
    val unit = JavaParser.parse(new NonClosingInputStream(input))
    val imports = getImports(unit)
    val prefix = packagePrefix(unit)
    for {
      className <- getClassNames(unit)
    } yield Ast(imports, (prefix :+ className).mkString("."))
  }

  private def getImports(unit: CompilationUnit): Seq[String] =
    for {
      anyImport <- orEmpty(unit.getImports)
      if !anyImport.isStatic && !anyImport.isAsterisk
    } yield extractName(anyImport.getName).mkString(".")

  private def orEmpty[A](value: java.util.List[A]): Seq[A] =
    Option(value).map(_.toSeq).getOrElse(Seq())

  private def getClassNames(unit: CompilationUnit): Seq[String] =
    for {
      typ <- orEmpty(unit.getTypes)
      if typ.getName ne null
    } yield typ.getName

  private def packagePrefix(unit: CompilationUnit): Seq[String] =
    if (unit.getPackage ne null) {
      extractName(unit.getPackage.getName)
    } else {
      Seq()
    }

  @tailrec
  private def extractName(expr: NameExpr, prefix: List[String] = List.empty[String]): Seq[String] =
    expr match {
      case e: QualifiedNameExpr => extractName(e.getQualifier, e.getName :: prefix)
      case e: NameExpr => e.getName :: prefix
    }
}
