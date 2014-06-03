package magnify.model

/**
 * @author Cezary Bartoszuk (cezarybartoszuk@gmail.com)
 * @author Rafal Matusiak (rafal.matusiak@gmail.com)
 */
final case class Ast (imports: Seq[String], className: String, calls: Seq[String])
