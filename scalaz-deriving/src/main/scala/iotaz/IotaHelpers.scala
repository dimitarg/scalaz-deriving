// Copyright: 2017 - 2018 Sam Halliday
// License: http://www.gnu.org/licenses/lgpl-3.0.en.html

package iotaz

import java.lang.String

import scala.{ Any, AnyVal, Int }
import scala.annotation.switch
import scala.collection.immutable.{ List, Seq }
import scala.collection.breakOut

import TList._
import TList.Compute.{ Aux => ↦ }
import TList.Op.{ Map => ƒ }

import scalaz._, Scalaz._
import Isomorphism.IsoSet

import Prods.Label

final class ProdGen[A, Repr <: TList, Labels <: TList] private (
  val from: A => Prod[Repr],
  val to: Prod[Repr] => A,
  val labels: Prod[Labels],
  val name: String
) extends IsoSet[Prod[Repr], A]
object ProdGen {
  def apply[A, R <: TList, L <: TList](
    f: A => Prod[R],
    t: Prod[R] => A,
    n: Prod[L],
    s: String
  )(
    implicit @unused ev: Label ƒ R ↦ L
  ): ProdGen[A, R, L] = new ProdGen(f, t, n, s)

  def gen[A, R <: TList, L <: TList]: ProdGen[A, R, L] =
    macro IotaMacros.prodGen[A, R, L]
}

final class CopGen[A, Repr <: TList, Labels <: TList] private (
  val from: A => Cop[Repr],
  val to: Cop[Repr] => A,
  val labels: Prod[Labels],
  val name: String
) extends IsoSet[Cop[Repr], A]
object CopGen {
  def apply[A, R <: TList, L <: TList](
    f: A => Cop[R],
    t: Cop[R] => A,
    n: Prod[L],
    s: String
  )(
    implicit @unused ev: Label ƒ R ↦ L
  ): CopGen[A, R, L] = new CopGen(f, t, n, s)

  def gen[A, R <: TList, L <: TList]: CopGen[A, R, L] =
    macro IotaMacros.copGen[A, R, L]
}

// unintentional joke about the state of northern irish politics...
object LazyProd {
  def apply[A1](a1: =>A1): Prod[Name[A1] :: TNil] = Prod(Need(a1))
  def apply[A1, A2](a1: =>A1, a2: =>A2): Prod[Name[A1] :: Name[A2] :: TNil] =
    Prod(Need(a1), Need(a2))
  def apply[A1, A2, A3](
    a1: =>A1,
    a2: =>A2,
    a3: =>A3
  ): Prod[Name[A1] :: Name[A2] :: Name[A3] :: TNil] =
    Prod(Need(a1), Need(a2), Need(a3))
  def apply[A1, A2, A3, A4](
    a1: =>A1,
    a2: =>A2,
    a3: =>A3,
    a4: =>A4
  ): Prod[Name[A1] :: Name[A2] :: Name[A3] :: Name[A4] :: TNil] =
    Prod(Need(a1), Need(a2), Need(a3), Need(a4))
}

object Prods {
  type Label[a] = String

  val empty: Prod[TNil] = Prod()
  def from1T[A1](e: A1): Prod[A1 :: TNil] =
    Prod.unsafeApply(List(e))
  def from2T[A1, A2](e: (A1, A2)): Prod[A1 :: A2 :: TNil] =
    Prod.unsafeApply(List(e._1, e._2))
  def from3T[A1, A2, A3](e: (A1, A2, A3)): Prod[A1 :: A2 :: A3 :: TNil] =
    Prod.unsafeApply(List(e._1, e._2, e._3))
  def from4T[A1, A2, A3, A4](
    e: (A1, A2, A3, A4)
  ): Prod[A1 :: A2 :: A3 :: A4 :: TNil] =
    Prod.unsafeApply(List(e._1, e._2, e._3, e._4))

  def to1T[A1](a: Prod[A1 :: TNil]): A1 = a.values(0).asInstanceOf[A1]
  def to2T[A1, A2](a: Prod[A1 :: A2 :: TNil]): (A1, A2) = (
    a.values(0).asInstanceOf[A1],
    a.values(1).asInstanceOf[A2]
  )
  def to3T[A1, A2, A3](a: Prod[A1 :: A2 :: A3 :: TNil]): (A1, A2, A3) = (
    a.values(0).asInstanceOf[A1],
    a.values(1).asInstanceOf[A2],
    a.values(2).asInstanceOf[A3]
  )
  def to4T[A1, A2, A3, A4](
    a: Prod[A1 :: A2 :: A3 :: A4 :: TNil]
  ): (A1, A2, A3, A4) = (
    a.values(0).asInstanceOf[A1],
    a.values(1).asInstanceOf[A2],
    a.values(2).asInstanceOf[A3],
    a.values(3).asInstanceOf[A4]
  )

  object ops {

    final implicit class UnsafeProds[T <: TList](
      private val self: Prod[T]
    ) extends AnyVal {
      def as[A <: TList]: Prod[A] = self.asInstanceOf[Prod[A]]
    }

    implicit final class ProdOps[A <: TList](private val a: Prod[A])
        extends AnyVal {
      def zip[B <: TList, H[_]](b: Prod[B])(
        implicit ev: H ƒ A ↦ B
      ): List[Id /~\ H] = {
        val _ = ev

        a.values
          .zip(b.values.asInstanceOf[Seq[H[Any]]])
          .map {
            case (a, h) => /~\[Id, H, Any](a, h)
          }(breakOut)
      }

      def zip[B <: TList, C <: TList, H[_]](b: Prod[B], c: Prod[C])(
        implicit
        ev1: H ƒ A ↦ B,
        ev2: Label ƒ A ↦ C
      ): List[(String, ?) /~\ H] = {
        val _ = (ev1, ev2)

        c.values
          .asInstanceOf[Seq[String]]
          .zip(a.values)
          .zip(b.values.asInstanceOf[Seq[H[Any]]])
          .map {
            case (a, h) => /~\[(String, ?), H, Any](a, h)
          }(breakOut)
      }

      def traverse[B <: TList, F[_], G[_]: Applicative](f: F ~> G)(
        implicit ev1: F ƒ B ↦ A
      ): G[Prod[B]] = {
        val _ = ev1
        a.values
          .asInstanceOf[Seq[F[Any]]]
          .toList
          .traverse(f)
          .map(bs => Prod.unsafeApply[B](bs))
      }

      def traverse[B <: TList, C <: TList, F[_], G[_]: Applicative](
        f: λ[α => (String, F[α])] ~> G,
        c: Prod[C]
      )(
        implicit ev1: F ƒ B ↦ A,
        ev2: Label ƒ B ↦ C
      ): G[Prod[B]] = {
        val _ = (ev1, ev2)
        c.values
          .asInstanceOf[Seq[String]]
          .zip(a.values.asInstanceOf[Seq[F[Any]]])
          .toList
          .traverse(f(_))
          .map(bs => Prod.unsafeApply[B](bs))
      }

      def ziptraverse2[B <: TList, C <: TList, F[_], G[_]: Applicative](
        b1: Prod[B],
        b2: Prod[B],
        f: λ[α => (Pair[α], F[α])] ~> G
      )(
        implicit ev1: F ƒ B ↦ A
      ): G[Prod[C]] = {
        val _ = ev1
        b1.values
          .zip(b2.values)
          .zip(a.values.asInstanceOf[Seq[F[Any]]])
          .toList
          .traverse(f(_))
          .map(bs => Prod.unsafeApply[C](bs))
      }

      def coptraverse[B <: TList, F[_], G[_]: Applicative](
        f: F ~> λ[α => Maybe[G[α]]]
      )(
        implicit ev1: F ƒ B ↦ A
      ): IStream[G[Cop[B]]] = {
        val _ = ev1
        IStream
          .fromFoldable(
            a.values
              .asInstanceOf[Seq[F[Any]]]
              .toList
              .indexed
          )
          .flatMap {
            case (i, fa) => IStream.fromMaybe(f(fa).map(g => (i, g)))
          }
          .map { case (i, g) => g.map(y => Cop.unsafeApply[B, Any](i, y)) }
      }

      def coptraverse[B <: TList, C <: TList, F[_], G[_]: Applicative](
        f: λ[α => (String, F[α])] ~> λ[α => Maybe[G[α]]],
        c: Prod[C]
      )(
        implicit ev1: F ƒ B ↦ A,
        ev2: Label ƒ B ↦ C
      ): IStream[G[Cop[B]]] = {
        val _ = (ev1, ev2)
        IStream
          .fromFoldable(
            c.values
              .asInstanceOf[Seq[String]]
              .zip(a.values.asInstanceOf[Seq[F[Any]]])
              .toList
              .indexed
          )
          .flatMap {
            case (i, sfa) => IStream.fromMaybe(f(sfa).map(y => (i, y)))
          }
          .map { case (i, g) => g.map(y => Cop.unsafeApply[B, Any](i, y)) }
      }
    }

    type Pair[a] = (a, a)
    implicit final class ProdOps2[A <: TList](
      private val as: (Prod[A], Prod[A])
    ) extends AnyVal {
      def zip[B <: TList, H[_]](b: Prod[B])(
        implicit ev: H ƒ A ↦ B
      ): List[Pair /~\ H] = {
        val _        = ev
        val (a1, a2) = as
        a1.values
          .zip(a2.values)
          .zip(b.values.asInstanceOf[Seq[H[Any]]])
          .map {
            case (aa, h) => /~\[Pair, H, Any](aa, h)
          }(breakOut)
      }
    }
  }

}

object Cops {
  def from1[A1](e: A1): Cop[A1 :: TNil] = Cop.unsafeApply(0, e)
  def from2[A1, A2](e: A1 \/ A2): Cop[A1 :: A2 :: TNil] = e match {
    case -\/(a) => Cop.unsafeApply(0, a)
    case \/-(b) => Cop.unsafeApply(1, b)
  }
  def from3[A1, A2, A3](e: A1 \/ (A2 \/ A3)): Cop[A1 :: A2 :: A3 :: TNil] =
    e match {
      case -\/(a)      => Cop.unsafeApply(0, a)
      case \/-(-\/(b)) => Cop.unsafeApply(1, b)
      case \/-(\/-(c)) => Cop.unsafeApply(2, c)
    }
  def from4[A1, A2, A3, A4](
    e: A1 \/ (A2 \/ (A3 \/ A4))
  ): Cop[A1 :: A2 :: A3 :: A4 :: TNil] =
    e match {
      case -\/(a)           => Cop.unsafeApply(0, a)
      case \/-(-\/(b))      => Cop.unsafeApply(1, b)
      case \/-(\/-(-\/(c))) => Cop.unsafeApply(2, c)
      case \/-(\/-(\/-(d))) => Cop.unsafeApply(3, d)
    }

  def to1[A1](c: Cop[A1 :: TNil]): A1 = c.value.asInstanceOf[A1]
  def to2[A1, A2](c: Cop[A1 :: A2 :: TNil]): A1 \/ A2 =
    (c.index: @switch) match {
      case 0 => -\/(c.value.asInstanceOf[A1])
      case 1 => \/-(c.value.asInstanceOf[A2])
    }
  def to3[A1, A2, A3](c: Cop[A1 :: A2 :: A3 :: TNil]): A1 \/ (A2 \/ A3) =
    (c.index: @switch) match {
      case 0 => -\/(c.value.asInstanceOf[A1])
      case 1 => \/-(-\/(c.value.asInstanceOf[A2]))
      case 2 => \/-(\/-(c.value.asInstanceOf[A3]))
    }
  def to4[A1, A2, A3, A4](
    c: Cop[A1 :: A2 :: A3 :: A4 :: TNil]
  ): A1 \/ (A2 \/ (A3 \/ A4)) = (c.index: @switch) match {
    case 0 => -\/(c.value.asInstanceOf[A1])
    case 1 => \/-(-\/(c.value.asInstanceOf[A2]))
    case 2 => \/-(\/-(-\/(c.value.asInstanceOf[A3])))
    case 3 => \/-(\/-(\/-(c.value.asInstanceOf[A4])))
  }

  object ops {
    final implicit class UnsafeCops[T <: TList](
      private val self: Cop[T]
    ) extends AnyVal {
      // a completely unsafe operation that is useful when we have some runtime
      // information like we create a
      //
      //   Any :: Any :: Any :: TNil
      //
      // and we know it is an instance of the more general type A
      def as[A <: TList]: Cop[A] = self.asInstanceOf[Cop[A]]

      def shift(i: Int): Cop[T] =
        Cop.unsafeApply[T, Any](self.index + i, self.value)
    }

    implicit final class CopOps[A <: TList](private val a: Cop[A])
        extends AnyVal {

      def zip[B <: TList, H[_]](b: Prod[B])(
        implicit ev: H ƒ A ↦ B
      ): Id /~\ H = {
        val _ = ev
        /~\[Id, H, Any](
          a.value,
          b.values(a.index).asInstanceOf[H[Any]]
        )
      }

      def zip[B <: TList, C <: TList, H[_]](b: Prod[B], c: Prod[C])(
        implicit ev1: H ƒ A ↦ B,
        ev2: Label ƒ A ↦ C
      ): (String, ?) /~\ H = {
        val _ = (ev1, ev2)

        /~\[(String, ?), H, Any](
          (c.values(a.index).asInstanceOf[String], a.value),
          b.values(a.index).asInstanceOf[H[Any]]
        )
      }

    }

    type Pair[a] = (a, a)
    implicit final class CopOps2[A <: TList](private val as: (Cop[A], Cop[A]))
        extends AnyVal {
      def zip[B <: TList, H[_]](b: Prod[B])(
        implicit ev: H ƒ A ↦ B
      ): (Int, Id /~\ H, Int, Id /~\ H) \/ (Pair /~\ H) = {
        val _        = ev
        val (a1, a2) = as
        val bs       = b.values.asInstanceOf[Seq[H[Any]]]
        val b1       = bs(a1.index)
        if (a1.index == a2.index)
          \/-(/~\[Pair, H, Any]((a1.value, a2.value), b1))
        else {
          val b2 = bs(a2.index)
          val e1 = /~\[Id, H, Any](a1.value, b1)
          val e2 = /~\[Id, H, Any](a2.value, b2)
          -\/((a1.index, e1, a2.index, e2))
        }
      }
    }
  }
}
