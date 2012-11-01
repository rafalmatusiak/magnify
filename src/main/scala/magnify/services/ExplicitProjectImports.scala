package magnify.services

import magnify.features.Imports
import magnify.model.java.Ast


final class ExplicitProjectImports extends Imports {
  /**
   * Resolves only explicit imports as:
   *
   * {{{
   *   import magnify.model.java.Ast
   * }}}
   *
   * Does not resolve implicit "same package imports", asterisk imports and static imports.
   */
  override def resolve(classes: Seq[Ast]): Map[String, Seq[String]] = {
    val classNames = classes.map(_.className).toSet
    val imports = for {
      Ast(imports, name) <- classes
    } yield (name, imports.filter(classNames))
    imports.toMap
  }
}
