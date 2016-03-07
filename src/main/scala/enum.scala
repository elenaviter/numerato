package numerato

import scala.annotation.StaticAnnotation
import scala.reflect.macros.whitebox

class enum extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro EnumMacros.decl
}

class EnumMacros(val c: whitebox.Context) {
  import c.universe._

  def decl(annottees: c.Expr[Any]*) = {
    annottees.map(_.tree) match {
      case tree @ List(q"class $enumType { ..$body }") =>
        val values = body.flatMap {
          case q"""val $value = Value""" => value :: Nil
          case _ => Nil
        }
        val bodyParts = values.zipWithIndex.map {
          case (value, index) =>
            q"""case object $value extends $enumType($index, ${s"$value"})"""
        }
        q"""
          sealed abstract class $enumType(val index: Int, val name: String)(implicit sealant: ${enumType.toTermName}.Sealant)
          object ${enumType.toTermName} {
            @scala.annotation.implicitNotFound(msg = "Enum types annotated with @enum can not be extended directly. To add another value to the enum, please adjust your `def ... = Value` declaration.")
            protected sealed abstract class Sealant
            protected implicit object Sealant extends Sealant
            ..$bodyParts
            val values: List[$enumType] = List(..$values)
            val fromIndex: Int => $enumType = Map(..${values.map(value => q"$value.index -> $value")})
            val fromName: String => $enumType = Map(..${values.map(value => q"$value.name -> $value")})
            def switch[A](pf: PartialFunction[Status, A]): Status => A =
              macro numerato.SwitchMacros.switch_impl[$enumType, A]
          }
        """
    }
  }
}
