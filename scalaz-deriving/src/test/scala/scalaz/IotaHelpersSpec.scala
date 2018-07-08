// Copyright: 2017 - 2018 Sam Halliday
// License: http://www.gnu.org/licenses/lgpl-3.0.en.html

package scalaz

import java.lang.String

import scala.Int
import scala.collection.immutable.List

import iotaz._
import iotaz.TList._

import org.scalatest._
import org.scalatest.Matchers._

object IotaHelpersSpec {
  final case class Foo(s: String, ɩ: Int)

  final case class Goo[A](s: String, a: A)

  object Bar
  case object CBar

  sealed trait Traity1
  final case class Traity1A(s: String) extends Traity1
  case object Traity1B                 extends Traity1

  sealed trait Traity2
  final case class Traity2A[A](s: A) extends Traity2
  case object Traity2B               extends Traity2

  sealed trait ATree
  final case class Leaf(value: String)               extends ATree
  final case class Branch(left: ATree, right: ATree) extends ATree

  sealed trait GTree[A]
  final case class GLeaf[A](value: A)                          extends GTree[A]
  final case class GBranch[A](left: GTree[A], right: GTree[A]) extends GTree[A]
}

class IotaHelpersSpec extends FlatSpec {
  import IotaHelpersSpec._

  "ProdGen" should "support case classes" in {
    val foo = Foo("hello", 13)
    val gen = ProdGen.gen[Foo, String :: Int :: TNil, String :: String :: TNil]
    gen.to(gen.from(foo)).shouldBe(foo)
    gen.labels.values.shouldBe(List("s", "ɩ"))
  }

  it should "support higher kinded case classes" in {
    val goo = Goo("hello", 13)

    def gen[A] =
      ProdGen.gen[Goo[Int], String :: Int :: TNil, String :: String :: TNil]
    val geni = gen[Int]

    geni.to(geni.from(goo)).shouldBe(goo)
    geni.labels.values.shouldBe(List("s", "a"))
  }

  it should "support objects" in {
    val gen = ProdGen.gen[Bar.type, TNil, TNil]
    gen.to(gen.from(Bar)).shouldBe(Bar)
    gen.labels.values.shouldBe(empty)
  }

  it should "support case objects" in {
    val gen = ProdGen.gen[CBar.type, TNil, TNil]
    gen.to(gen.from(CBar)).shouldBe(CBar)
    gen.labels.values.shouldBe(empty)
  }

  "CopGen" should "support sealed traits" in {
    val gen =
      CopGen
        .gen[
          Traity1,
          Traity1A :: Traity1B.type :: TNil,
          String :: String :: TNil
        ]

    val a = Traity1A("hello")
    gen.to(gen.from(a)).shouldBe(a)

    val b = Traity1B
    gen.to(gen.from(b)).shouldBe(b)

    gen.labels.values.shouldBe(List("Traity1A", "Traity1B"))
  }

  it should "support sealed traits with generic parameters" in {
    def genG[A1] =
      CopGen
        .gen[Traity2, Traity2A[A1] :: Traity2B.type :: TNil, String :: String :: TNil]

    val gen = genG[String]

    val a = Traity2A("hello")
    gen.to(gen.from(a)).shouldBe(a)

    val b = Traity2B
    gen.to(gen.from(b)).shouldBe(b)

    gen.labels.values.shouldBe(List("Traity2A", "Traity2B"))

    val gena =
      CopGen
        .gen[Traity2, Traity2A[scala.Any] :: Traity2B.type :: TNil, String :: String :: TNil]

    gena.to(gena.from(a)).shouldBe(a)
    gena.to(gena.from(b)).shouldBe(b)

  }

  it should "support recursive ADTs" in {
    val gen =
      CopGen.gen[ATree, Leaf :: Branch :: TNil, String :: String :: TNil]

    val a = Leaf("foo")
    gen.to(gen.from(a)).shouldBe(a)

    val b = Branch(a, a)
    gen.to(gen.from(b)).shouldBe(b)

    gen.labels.values.shouldBe(List("Leaf", "Branch"))
  }

  it should "support recursive GADTs" in {
    def gen[A] =
      CopGen
        .gen[GTree[A], GLeaf[A] :: GBranch[A] :: TNil, String :: String :: TNil]

    val gens = gen[String]

    val a = GLeaf("foo")
    gens.to(gens.from(a)).shouldBe(a)

    val b = GBranch(a, a)
    gens.to(gens.from(b)).shouldBe(b)

    gen.labels.values.shouldBe(List("GLeaf", "GBranch"))
  }

}