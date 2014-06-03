package magnify.services

import javax.lang.model.element._
import javax.lang.model.`type`.{ExecutableType, TypeMirror}
import javax.lang.model.util.Types
import javax.tools._

import com.sun.source.tree.{ClassTree, CompilationUnitTree, ExpressionTree, MemberSelectTree, MethodInvocationTree}
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
import magnify.model.Ast


/**
 * @author Rafal Matusiak (rafal.matusiak@gmail.com)
 */
private[services] final class JavacParser extends Parser {

  val logger = Logger(classOf[JavacParser].getSimpleName)

  //TODO: add other dependencies: field access, new / constructor call, interfaces ?
  class AstTransformer(trees: Trees, types: Types) extends TreePathScanner[Seq[(Ast, String)], Void]  {
    var currentAst: Option[Ast] = None

    private def typeOf(node: ExpressionTree): TypeMirror =
      trees.getTypeMirror(TreePath.getPath(getCurrentPath, node))

    private def asElement(t: TypeMirror): Element =
      types.asElement(t)

    //TODO: consider support for nested classes
    private def enclosingType(elem: Element): Option[TypeElement] =
      elem match {
        case e: TypeElement if (e ne e.getSimpleName) && (e.getSimpleName.length() > 0) => Some(e)
        case e: Element => enclosingType(elem.getEnclosingElement)
        case _ => None
      }

    private def findCaller(node: MethodInvocationTree): Option[ExecutableElement] =
      Option(trees.getScope(getCurrentPath).getEnclosingMethod)

    private def findCallee(node: MethodInvocationTree): Option[ExecutableElement] =
      node.getMethodSelect match {
        case s :MemberSelectTree => findCallee(s)
        case _ => None
      }

    private def findCallee(node: MemberSelectTree): Option[ExecutableElement] =
      (asElement(typeOf(node.getExpression)), typeOf(node)) match {
        case (t :TypeElement, m: ExecutableType) => findMethod(t, m, node.getIdentifier)
        case _ => None
      }

    private def findMethod(declaredType :TypeElement, methodType: ExecutableType, methodName: Name): Option[ExecutableElement] =
      declaredType.getEnclosedElements.toList.filter({
          case ex: ExecutableElement =>
            ex.getSimpleName.equals(methodName) && types.isSubsignature(methodType, ex.asType().asInstanceOf[ExecutableType])
          case _ => false
      }) match {
        case m :: _ => Some(m.asInstanceOf[ExecutableElement])
        case _ => None
      }

    private def lift(call: (ExecutableElement, ExecutableElement)): Option[(TypeElement, TypeElement)] = {
      val (caller, callee) = call
      (enclosingType(caller), enclosingType(callee)) match {
        case (Some(callerType), Some(calleeType)) => Some(callerType, calleeType)
        case _ => None
      }
    }

    private def asCall(node: MethodInvocationTree): Option[(ExecutableElement, ExecutableElement)] =
      try {
        for {
          caller <- findCaller(node)
          callee <- findCallee(node)
        } yield (caller, callee)
      } catch {
        case e: Exception => None
      }

    private def asClassCall(node: MethodInvocationTree): Option[(TypeElement, TypeElement)] =
      asCall(node) match {
        case Some(call) => lift(call)
        case _ => None
      }

    override def visitMethodInvocation(node: MethodInvocationTree, p: Void) =
      (asClassCall(node), currentAst) match {
        case (Some((caller, callee)), Some(ast)) =>
          currentAst = Some(Ast(ast.imports, ast.className, ast.calls :+ callee.getQualifiedName.toString))
          super.visitMethodInvocation(node, p)
        case _ => super.visitMethodInvocation(node, p)
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
          //TODO: for now, add all imported classes as potential callees
          //currentAst = Some(Ast(imported, typ.getQualifiedName.toString, Seq()))
          currentAst = Some(Ast(imported, typ.getQualifiedName.toString, imported))
          //TODO: define string content
          //val stringContent = node.toString
          val stringContent = getCurrentPath.getCompilationUnit.toString
          super.visitClass(node, p)
          Seq((currentAst.get, stringContent))
        case _ => super.visitClass(node, p)
      }

    private def orEmpty[A](value: java.util.List[A]): Seq[A] =
      Option(value).map(_.toSeq).getOrElse(Seq())

    override def reduce(r1: Seq[(Ast, String)], r2: Seq[(Ast, String)]) =
      (if (r1 ne null) r1 else Seq()) ++ (if (r2 ne null) r2 else Seq())

    def transform(unit: CompilationUnitTree): Seq[(Ast, String)] =
      scan(unit, null)

  }

  class CharSequenceJavaFileObject(name: String, content: CharSequence)
    extends SimpleJavaFileObject(URI.create("string:///"), JavaFileObject.Kind.SOURCE) {

    override def getCharContent(ignoreEncodingErrors: Boolean): CharSequence = content
  }

  class JavaClassObject(name: String, kind: JavaFileObject.Kind)
    extends SimpleJavaFileObject(URI.create("string:///" + name.replace('.', '/') + kind.extension), kind) {

    private val os = new ByteArrayOutputStream()

    def bytes = os.toByteArray

    override def openOutputStream(): OutputStream = os
  }

  class ClassFileManager(standardManager: StandardJavaFileManager)
    extends ForwardingJavaFileManager[StandardJavaFileManager](standardManager) {

    override def getJavaFileForInput(location: JavaFileManager.Location, className: String, kind: JavaFileObject.Kind): JavaFileObject =
      new JavaClassObject(className, kind)
  }

  override def apply(inputs: Seq[(String, InputStream)]): Seq[(Ast, String)] =
    parse(inputs)

  private def parse(inputs: Seq[(String, InputStream)]): Seq[(Ast, String)] =
    try {
      val contents = inputs map {
        case (name, input) => (name, scala.io.Source.fromInputStream(input).getLines().mkString("\n"))
      }

      val compiler = JavacTool.create() //ToolProvider.getSystemJavaCompiler()
      val fileManager = new ClassFileManager(compiler.getStandardFileManager(null, null, null))
      val fileObjects = contents map {
        case (name, content) => new CharSequenceJavaFileObject(name, content)
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
