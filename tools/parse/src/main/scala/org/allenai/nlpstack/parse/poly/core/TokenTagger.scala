package org.allenai.nlpstack.parse.poly.core

/** A "tag" for a token, consisting of a symbolic name and a symbolic value. */
case class TokenTag(name: Symbol, value: Symbol)

/** A TokenTagger is any module that takes a token and produces a set of TokenTags as output. */
trait TokenTagger {
  def tag(token: Token): Set[TokenTag]
}

/** A KeywordTagger tags any token whose .word field is found in a list of keywords.
  *
  * Note that this tagger is case-insensitive.
  *
  * @param keywords the list of keywords
  */
case class KeywordTagger(keywords: Set[String]) extends TokenTagger {

  val taggerName = 'keyword

  private val lcKeywords = keywords map { _.toLowerCase }

  def tag(token: Token): Set[TokenTag] = {
    val tokStr = token.word.name.toLowerCase
    if (lcKeywords.contains(tokStr)) {
      Set(TokenTag(taggerName, Symbol(tokStr)))
    } else {
      Set()
    }
  }
}

/** The LexicalPropertiesTagger tags a token with a set of lexical characteristics.
  *
  * Specifically, a token gets the tag:
  * - TokenTag('lexical, 'firstCap) if its first character is a capital letter
  * - TokenTag('lexical, 'existsCap) if any character is a capital letter
  * - TokenTag('lexical, 'allCaps) if all characters are capital letters
  * - TokenTag('lexical, 'existsNum) if it contains a digit
  */
case object LexicalPropertiesTagger extends TokenTagger {

  val taggerName = 'lexical

  def tag(token: Token): Set[TokenTag] = {
    val tokStr = token.word.name
    val firstLetterCapital = tokStr.headOption match {
      case Some(x) if Character.isUpperCase(x) && Character.isAlphabetic(x) => Some('firstCap)
      case _ => None
    }
    val existsCapital = tokStr find {
      { x => Character.isUpperCase(x) && Character.isAlphabetic(x) }
    } map {
      _ => 'existsCap
    }
    val allCaps =
      if (tokStr forall { x => Character.isUpperCase(x) && Character.isAlphabetic(x) }) {
        Some('allCaps)
      } else {
        None
      }
    val existsNumber = tokStr find {
      Character.isDigit
    } map {
      _ => 'existsNum
    }
    def parseDouble(s: String) = try { Some(s.toDouble) } catch { case _: Throwable => None }
    val looksLikeDouble = parseDouble(tokStr) map {
      _ => 'looksLikeDouble
    }
    val looksLikeGeomLabel =
      if (tokStr forall { x => Set('A', 'B', 'C', 'D', 'E', 'F').contains(x) }) {
        Some('looksLikeGeo)
      } else {
        None
      }
    (Seq(firstLetterCapital, existsCapital, allCaps, existsNumber,
      looksLikeDouble, looksLikeGeomLabel) map { maybeFeat =>
      maybeFeat map { feat =>
        TokenTag(taggerName, feat)
      }
    }).flatten.toSet
  }
}

