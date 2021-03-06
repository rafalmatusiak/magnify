package magnify.services

import javax.lang.model.element._
import javax.lang.model.`type`.TypeMirror
import javax.lang.model.util.Types
import javax.tools._

import com.sun.source.tree._
import com.sun.source.util.TreePath
import com.sun.source.util.TreePathScanner
import com.sun.source.util.Trees
import com.sun.tools.javac.api.{JavacTool, JavacTaskImpl}

import java.io._
import magnify.features.Parser
import play.api.Logger
import java.net.URI
import scala.collection.JavaConversions._
import scala.Some
import scala.util.Try
import magnify.model.Ast


/**
 * @author Rafal Matusiak (rafal.matusiak@gmail.com)
 */
private[services] final class JavacParser extends Parser {

  val logger = Logger(classOf[JavacParser].getSimpleName)

  //TODO: add other dependencies: field access, inheritance, via interfaces ?
  class AstTransformer(trees: Trees, types: Types) extends TreePathScanner[Seq[(Ast, String)], Void]  {
    var currentAst: Option[Ast] = None

    private def typeOf(node: ExpressionTree): Option[TypeMirror] =
      Option(trees.getTypeMirror(TreePath.getPath(getCurrentPath.getCompilationUnit, node)))

    private def asElement(tm: TypeMirror): Option[Element] =
      Option(types.asElement(tm))

    private def asTypeElement(node: ExpressionTree): Option[TypeElement] =
      typeOf(node) match {
        case Some(tm) => asElement(tm) match {
          case Some(te: TypeElement) => Some(te)
          case _ => None
        }
        case _ => None
      }

    private def enclosingType(node: ExpressionTree): Option[TypeElement] =
      Option(trees.getScope(TreePath.getPath(getCurrentPath.getCompilationUnit, node)).getEnclosingClass)

    private def asClassCall(node: MethodInvocationTree): Option[(TypeElement, TypeElement)] =
      node.getMethodSelect match {
        case s: MemberSelectTree => (enclosingType(node), asTypeElement(s.getExpression)) match {
          case (Some(caller), Some(callee)) => Some(caller, callee)
          case _ => None
        }
        case _ => None
      }

    override def visitMethodInvocation(node: MethodInvocationTree, p: Void) =
      (Try(asClassCall(node)).getOrElse(None), currentAst) match {
        case (Some((caller, callee)), Some(ast)) =>
          currentAst = Some(Ast(ast.imports, ast.className, ast.calls :+ callee.getQualifiedName.toString))
          super.visitMethodInvocation(node, p)
        case _ => super.visitMethodInvocation(node, p)
      }

    private def asClassCall(node: NewClassTree): Option[(TypeElement, TypeElement)] =
      (enclosingType(node), asTypeElement(node.getIdentifier)) match {
        case (Some(caller), Some(callee)) => Some(caller, callee)
        case _ => None
      }

    override def visitNewClass(node: NewClassTree, p: Void) =
      (Try(asClassCall(node)).getOrElse(None), currentAst) match {
        case (Some((caller, callee)), Some(ast)) =>
          currentAst = Some(Ast(ast.imports, ast.className, ast.calls :+ callee.getQualifiedName.toString))
          super.visitNewClass(node, p)
        case _ => super.visitNewClass(node, p)
      }

    private def imports(node: ClassTree): Seq[String] =
      for {
        anyImport <- orEmpty(getCurrentPath.getCompilationUnit.getImports)
        if !anyImport.isStatic// && !anyImport.isAsterisk
      } yield anyImport.getQualifiedIdentifier.toString

    override def visitClass(node: ClassTree, p: Void) =
      trees.getElement(getCurrentPath) match {
        case typ :TypeElement if typ.getEnclosingElement.getKind == ElementKind.PACKAGE =>
          val imported = imports(node)
          currentAst = Some(Ast(imported, typ.getQualifiedName.toString, Seq()))
          //TODO: define string content
          //val stringContent = node.toString
          val stringContent = getCurrentPath.getCompilationUnit.getSourceFile.getCharContent(true).toString
          super.visitClass(node, p)
          Seq((currentAst.get, stringContent))
        case _ => super.visitClass(node, p)
      }

    private def orEmpty[A](value: java.util.List[A]): Seq[A] =
      Option(value).map(_.toSeq).getOrElse(Seq())

    override def reduce(r1: Seq[(Ast, String)], r2: Seq[(Ast, String)]) =
      Option(r1).getOrElse(Seq()) ++ Option(r2).getOrElse(Seq())

    def transform(unit: CompilationUnitTree): Seq[(Ast, String)] =
      Try(scan(unit, null)).getOrElse(Seq())

  }

  override def apply(inputs: Seq[(String, InputStream)]): Seq[(Ast, String)] =
    parse(inputs)

  private def parse(inputs: Seq[(String, InputStream)]): Seq[(Ast, String)] =
    try {
      val contents = inputs map {
        case (name, input) => (name, scala.io.Source.fromInputStream(input).getLines().mkString("\n"))
      }

      val compiler = JavacTool.create() //ToolProvider.getSystemJavaCompiler()
      val fileManager = compiler.getStandardFileManager(null, null, null)
      val fileObjects = contents map {
        case (name, content) => new SimpleJavaFileObject(URI.create("string:///" + name), JavaFileObject.Kind.SOURCE) {
          override def getCharContent(ignoreEncodingErrors: Boolean): CharSequence = content
        }
      }
      val javac = compiler.getTask(null, fileManager, null, null, null,
        fileObjects).asInstanceOf[JavacTaskImpl]

      val units = javac.parse().toSeq
      javac.analyze()
      val trees = Trees.instance(javac)
      val types = javac.getTypes

      val transformer = new AstTransformer(trees, types)

      for {
        unit <- units
        (ast, stringContent) <- transformer.transform(unit)
      } yield (ast, stringContent)
    } catch {
      case e: Exception =>
        logger.warn("Could not parse Java code.", e)
        Seq()
    }
}
