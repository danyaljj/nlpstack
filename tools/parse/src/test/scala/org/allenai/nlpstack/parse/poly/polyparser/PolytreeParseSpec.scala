package org.allenai.nlpstack.parse.poly.polyparser

import org.allenai.common.testkit.UnitSpec
import org.allenai.nlpstack.parse.poly.core.{ Sentence, NexusToken, Token }

object PolytreeParseTestData {
  // scalastyle:off

  val tokens1 = Vector(NexusToken, Token('we), Token('saw), Token('a), Token('white), Token('cat),
    Token('with), Token('a), Token('telescope))

  val breadcrumb1 = Vector(-1, 2, 0, 5, 5, 2, 2, 8, 6)

  val children1: Vector[Set[Int]] = Vector(Set(2), Set(), Set(1, 5, 6), Set(), Set(), Set(3, 4), Set(8),
    Set(), Set(7))

  val arclabels1: Vector[Set[(Int, ArcLabel)]] =
    Vector(
      Set((2, SingleSymbolArcLabel('root))),
      Set((2, SingleSymbolArcLabel('nsubj))),
      Set((1, SingleSymbolArcLabel('nsubj)), (5, SingleSymbolArcLabel('dobj)),
        (6, SingleSymbolArcLabel('prep))),
      Set((5, SingleSymbolArcLabel('det))),
      Set((5, SingleSymbolArcLabel('amod))),
      Set((2, SingleSymbolArcLabel('dobj)), (3, SingleSymbolArcLabel('det)),
        (4, SingleSymbolArcLabel('amod))),
      Set((2, SingleSymbolArcLabel('prep)), (8, SingleSymbolArcLabel('pobj))),
      Set((8, SingleSymbolArcLabel('det))),
      Set((6, SingleSymbolArcLabel('pobj)), (7, SingleSymbolArcLabel('det)))
    )

  val parse1 = PolytreeParse(Sentence(tokens1), breadcrumb1, children1, arclabels1)

  val children1b: Vector[Set[Int]] = Vector(Set(2), Set(), Set(1, 5), Set(5), Set(5), Set(), Set(2, 8),
    Set(8), Set())

  val parse1b = PolytreeParse(Sentence(tokens1), breadcrumb1, children1b, arclabels1)

}

class PolytreeParseSpec extends UnitSpec {
  // scalastyle:off

  /** This represents the following polytree parse:
    * format: OFF
    *
    * NEXUS_0
    *     |
    *     |       the_1--
    *     |              \
    *     |               -->cat_2
    *     \              /
    *      -----> sat_3--
    *        /
    * by_4 --
    *        \
    *         --> me_5
    *
    * format: ON
    */
  val parse2 = PolytreeParse(
    sentence = Sentence(Vector(NexusToken, Token('the), Token('cat), Token('sat),
      Token('by), Token('me))),
    breadcrumb = Vector(-1, 2, 3, 0, 3, 4),
    children = Vector(Set(3), Set(2), Set(), Set(2), Set(3, 5), Set()),
    arclabels =
      Vector(
        Set((3, SingleSymbolArcLabel('root))),
        Set((2, SingleSymbolArcLabel('det))),
        Set((1, SingleSymbolArcLabel('det)), (3, SingleSymbolArcLabel('nsubj))),
        Set((0, SingleSymbolArcLabel('root)), (2, SingleSymbolArcLabel('nsubj)),
          (4, SingleSymbolArcLabel('prep))),
        Set((3, SingleSymbolArcLabel('prep)), (5, SingleSymbolArcLabel('pobj))),
        Set((4, SingleSymbolArcLabel('pobj)))
      )
  )

  /** This represents the following polytree parse:
    *
    * NEXUS_0
    *     |
    *     |       the_1--
    *     |              \
    *     |               -->cat_2
    *     \              /
    *      -----> gave_3-
    *                   \ \
    *                   \  -->me_4
    *                   \
    *                    --> it_5
    *
    */
  val parse3 = PolytreeParse(
    sentence = Sentence(Vector(
      NexusToken,
      Token('the, Map('cpos -> Set('DT))),
      Token('cat, Map('cpos -> Set('NN))),
      Token('gave, Map('cpos -> Set('VB))),
      Token('me, Map('cpos -> Set('NN))),
      Token('it, Map('cpos -> Set('NN)))
    )),
    breadcrumb = Vector(-1, 2, 3, 0, 3, 3),
    children = Vector(Set(3), Set(2), Set(), Set(2, 4, 5), Set(), Set()),
    arclabels =
      Vector(
        Set((3, SingleSymbolArcLabel('root))),
        Set((2, SingleSymbolArcLabel('det))),
        Set((1, SingleSymbolArcLabel('det)), (3, SingleSymbolArcLabel('nsubj))),
        Set((0, SingleSymbolArcLabel('root)), (2, SingleSymbolArcLabel('nsubj)),
          (4, SingleSymbolArcLabel('dobj)), (5, SingleSymbolArcLabel('iobj))),
        Set((3, SingleSymbolArcLabel('dobj))),
        Set((4, SingleSymbolArcLabel('iobj)))
      )
  )

  "Calling .siblings" should "return the correct result for parse2" in {
    parse2.siblings(1) shouldBe Set()
    parse2.siblings(2) shouldBe Set(4)
    parse2.siblings(3) shouldBe Set()
    parse2.siblings(4) shouldBe Set(2)
    parse2.siblings(5) shouldBe Set()
  }

  it should "return the correct result for parse3" in {
    parse3.siblings(1) shouldBe Set()
    parse3.siblings(2) shouldBe Set(4, 5)
    parse3.siblings(3) shouldBe Set()
    parse3.siblings(4) shouldBe Set(2, 5)
    parse3.siblings(5) shouldBe Set(2, 4)
  }

  "Creating a PolytreeParse" should
    "throw an IllegalArgumentException if the input vectors have differing lengths" in {
      val tokens1Bad = PolytreeParseTestData.tokens1.init
      intercept[IllegalArgumentException] {
        new PolytreeParse(Sentence(tokens1Bad), PolytreeParseTestData.breadcrumb1,
          PolytreeParseTestData.children1, PolytreeParseTestData.arclabels1)
      }
    }

  it should "throw an IllegalArgumentException if the nexus breadcrumb is not -1" in {
    val breadcrumb1Bad = 0 +: PolytreeParseTestData.breadcrumb1.tail
    intercept[IllegalArgumentException] {
      new PolytreeParse(Sentence(PolytreeParseTestData.tokens1), breadcrumb1Bad,
        PolytreeParseTestData.children1, PolytreeParseTestData.arclabels1)
    }
  }

  "Calling .arcLabelByEndNodes" should "compute the correct value for parse1" in {
    PolytreeParseTestData.parse1.arcLabelByEndNodes shouldBe Map(
      Set(0, 2) -> SingleSymbolArcLabel('root),
      Set(1, 2) -> SingleSymbolArcLabel('nsubj),
      Set(2, 5) -> SingleSymbolArcLabel('dobj),
      Set(2, 6) -> SingleSymbolArcLabel('prep),
      Set(3, 5) -> SingleSymbolArcLabel('det),
      Set(4, 5) -> SingleSymbolArcLabel('amod),
      Set(6, 8) -> SingleSymbolArcLabel('pobj),
      Set(7, 8) -> SingleSymbolArcLabel('det)
    )
  }

  "Calling .areNeighbors" should "return true for a (head, tail) arc" in {
    PolytreeParseTestData.parse1.areNeighbors(2, 5) shouldBe true
  }

  it should "return true for a (tail, head) arc" in {
    PolytreeParseTestData.parse1.areNeighbors(5, 2) shouldBe true
  }

  it should "return false for a nonconnected pair of tokens" in {
    PolytreeParseTestData.parse1.areNeighbors(1, 5) shouldBe false
  }

  "Calling .paths" should "return the correct paths for parse1" in {
    PolytreeParseTestData.parse1.paths shouldBe Vector(List(), List(0, 2), List(0),
      List(0, 2, 5), List(0, 2, 5), List(0, 2), List(0, 2), List(0, 2, 6, 8), List(0, 2, 6))
  }

  "Calling .depthFirstPreorder" should "return the nodes in the correct order" in {
    PolytreeParseTestData.parse1.depthFirstPreorder shouldBe Vector(0, 2, 1, 5, 3, 4, 6, 8, 7)
  }


  /*
  "Calling .relativeCposMap" should "return the correct result for parse2" in {
    parse2.relativeCposMap(1) shouldBe Tuple2((true, 'DT), 0)
    parse2.relativeCposMap(2) shouldBe Tuple2((true, 'NN), 0)
    parse2.relativeCposMap(3) shouldBe Tuple2((false, 'VB), 0)
    parse2.relativeCposMap(4) shouldBe Tuple2((false, 'IN), 0)
    parse2.relativeCposMap(5) shouldBe Tuple2((false, 'PRP), 0)
  }

  it should "return the correct result for parse3" in {
    parse3.relativeCposMap(1) shouldBe Tuple2((true, 'DT), 0)
    parse3.relativeCposMap(2) shouldBe Tuple2((true, 'NN), 0)
    parse3.relativeCposMap(3) shouldBe Tuple2((false, 'VB), 0)
    parse3.relativeCposMap(4) shouldBe Tuple2((false, 'NN), 0)
    parse3.relativeCposMap(5) shouldBe Tuple2((false, 'NN), 1)
  }
  */
}
