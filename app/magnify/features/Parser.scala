package magnify.features

import java.io.InputStream
import magnify.model.Ast

/**
 * @author Cezary Bartoszuk (cezarybartoszuk@gmail.com)
 * @author Rafal Matusiak (rafal.matusiak@gmail.com)
 */
trait Parser extends (Seq[(String, InputStream)] => Seq[(Ast, String)])
