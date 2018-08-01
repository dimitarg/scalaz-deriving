// Copyright: 2010 - 2018 Sam Halliday
// License: http://www.gnu.org/licenses/lgpl-3.0.en.html

package jsonformat

import simulacrum._
import scalaz._, Scalaz._

import JsDecoder.ops._

@typeclass(generateAllOps = false) trait JsDecoder[A] {
  def fromJson(json: JsValue): String \/ A
}
object JsDecoder
    extends JsDecoderScalaz1
    with JsDecoderRefined
    with JsDecoderStdlib1
    with JsDecoderScalaz2
    with JsDecoderStdlib2 {
  object ops {
    implicit class JsValueExtras(private val j: JsValue) extends AnyVal {
      def as[A: JsDecoder]: String \/ A = JsDecoder[A].fromJson(j)
      def asJsObject: String \/ JsObject = j match {
        case obj @ JsObject(_) => obj.right
        case other             => fail("JsObject", other)
      }
    }
    implicit class JsObjectExtras(private val j: JsObject) extends AnyVal {
      def get(key: String): String \/ JsValue =
        j.fields.find(_._1 == key).map(_._2) \/> s"missing field '$key'"
      def getAs[A: JsDecoder](key: String): String \/ A =
        get(key).flatMap(_.as[A])
    }
  }

  @inline final def instance[A](f: JsValue => String \/ A): JsDecoder[A] = f(_)
  private type Sig[a] = JsValue => String \/ a
  private val iso = Kleisli.iso(
    λ[Sig ~> JsDecoder](instance(_)),
    λ[JsDecoder ~> Sig](_.fromJson)
  )
  implicit val monad: MonadError[JsDecoder, String] = MonadError.fromIso(iso)

  def fail[A](expected: String, got: JsValue): -\/[String] =
    -\/(s"expected $expected, got $got")

  implicit val jsValue: JsDecoder[JsValue] = _.right
  implicit val long: JsDecoder[Long] = {
    case JsInteger(n) => n.right
    case other        => fail("JsInteger", other)
  }
  implicit val double: JsDecoder[Double] = {
    case JsDouble(n)  => n.right
    case JsInteger(n) => n.toDouble.right // potential loss of precision
    case other        => fail("JsDouble or JsInteger", other)
  }
  implicit val boolean: JsDecoder[Boolean] = {
    case JsBoolean(x) => x.right
    case other        => fail("JsBoolean", other)
  }
  implicit val string: JsDecoder[String] = {
    case JsString(x) => x.right
    case other       => fail("JsString", other)
  }

  implicit val float: JsDecoder[Float] = double.emap {
    case n if n >= Float.MinValue && n <= Float.MaxValue => n.toFloat.right
    case other                                           => fail("64 bit floating point number", JsDouble(other))
  }
  implicit val int: JsDecoder[Int] = long.emap {
    case n if n >= Int.MinValue && n <= Int.MaxValue => n.toInt.right
    case other                                       => fail("32 bit integer", JsInteger(other))
  }
  implicit val short: JsDecoder[Short] = long.emap {
    case n if n >= Short.MinValue && n <= Short.MaxValue => n.toShort.right
    case other                                           => fail("16 bit integer", JsInteger(other))
  }
  implicit val byte: JsDecoder[Byte] = long.emap {
    case n if n >= Byte.MinValue && n <= Byte.MaxValue => n.toByte.right
    case other                                         => fail("8 bit integer", JsInteger(other))
  }
  implicit val unit: JsDecoder[Unit] = long.emap {
    case 1     => ().right
    case other => fail("1.0", JsInteger(other))
  }
  implicit val char: JsDecoder[Char] = string.emap {
    case str if str.length == 1 => str(0).right
    case other                  => fail("single character", JsString(other))
  }
  implicit val symbol: JsDecoder[Symbol] = string.map(Symbol(_))

}

private[jsonformat] trait JsDecoderScalaz1 {
  this: JsDecoder.type =>

  implicit def ilist[A: JsDecoder]: JsDecoder[IList[A]] = {
    case JsArray(js) => js.traverse(_.as[A])
    case other       => fail("JsArray", other)
  }

  implicit def nel[A: JsDecoder]: JsDecoder[NonEmptyList[A]] =
    ilist[A].emap(_.toNel \/> "empty list")

  implicit def maybe[A: JsDecoder]: JsDecoder[Maybe[A]] = {
    case JsNull => Maybe.empty.right
    case a      => a.as[A].map(_.just)
  }
  implicit def disjunction[A: JsDecoder, B: JsDecoder]: JsDecoder[A \/ B] = {
    v =>
      (v.as[A], v.as[B]) match {
        case (left @ \/-(_), -\/(_))  => left.map(_.left)
        case (-\/(_), right @ \/-(_)) => right.map(_.right)
        case (\/-(_), \/-(_))         => fail("No ambiguity", v)
        case (-\/(ea), -\/(eb))       => s"Left: ${ea}\nRight: ${eb}".left
      }
  }

}
private[jsonformat] trait JsDecoderRefined {
  this: JsDecoder.type =>

  import eu.timepit.refined.refineV
  import eu.timepit.refined.api._
  implicit def refined[A: JsDecoder, P](
    implicit V: Validate[A, P]
  ): JsDecoder[A Refined P] =
    JsDecoder[A].emap(refineV[P](_).disjunction)

}
private[jsonformat] trait JsDecoderStdlib1 {
  this: JsDecoder.type =>

  implicit def option[A: JsDecoder]: JsDecoder[Option[A]] =
    maybe[A].map(_.toOption)
  implicit def either[A: JsDecoder, B: JsDecoder]: JsDecoder[Either[A, B]] =
    disjunction[A, B].map(_.toEither)

  implicit def dict[A: JsDecoder]: JsDecoder[Map[String, A]] = {
    case JsObject(fields) =>
      fields.traverse {
        case (key, value) => value.as[A].strengthL(key)
      }.map(_.toList.toMap)
    case other => fail("JsObject", other)
  }

}
private[jsonformat] trait JsDecoderScalaz2 {
  this: JsDecoder.type =>

}
private[jsonformat] trait JsDecoderStdlib2 {
  this: JsDecoder.type =>

  import scala.collection.generic.CanBuildFrom
  implicit def cbf[T[_], A: JsDecoder](
    implicit CBF: CanBuildFrom[Nothing, A, T[A]]
  ): JsDecoder[T[A]] = ilist[A].map(_.toList.to[T])

}
