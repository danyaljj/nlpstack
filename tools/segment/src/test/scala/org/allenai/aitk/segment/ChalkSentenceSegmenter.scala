package org.allenai.tool
package segment

import org.allenai.common.testkit.UnitSpec

class ChalkSentencerSpec extends UnitSpec {
  val sentencer = new BreezeSentencer
  val document = "He went to work.  He bought a suit.  He ate a melon."
  "chalk sentencer" should "properly segment" in {
      val segments = sentencer.segment(document).toIndexedSeq
      assert(segments(0) === Segment("He went to work.  ", 0))
      assert(segments(1) === Segment("He bought a suit.  ", 18))
      assert(segments(2) === Segment("He ate a melon.", 37))
    }
  }
}
