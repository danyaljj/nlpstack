package org.allenai.nlpstack.parse.poly.polyparser

import java.nio.file.Paths

import org.allenai.datastore.Datastore
import org.allenai.nlpstack.parse.poly.core.{ Sentence, SentenceSource }
import org.allenai.nlpstack.parse.poly.fsm.{ SculptureSource, Sculpture }

/** A data source for PolytreeParse objects. */
trait PolytreeParseSource extends SentenceSource with SculptureSource {
  def parseIterator: Iterator[PolytreeParse]

  override def sculptureIterator: Iterator[Sculpture] = parseIterator
}

case class InMemoryPolytreeParseSource(
    parses: Iterable[PolytreeParse]
) extends PolytreeParseSource {

  override def parseIterator: Iterator[PolytreeParse] = {
    parses.iterator
  }

  override def sentenceIterator: Iterator[Sentence] = {
    parseIterator map { _.sentence }
  }
}

object PolytreeParseSource {

  /** Counts the number of tokens in a parse source (not including nexus tokens).
    *
    * @param parseSource the parse source
    * @param excludePunctuation true if you don't want to count punctutation tokens (i.e. tokens
    * whose .cpos property is ".")
    * @return the total number of tokens in the parse source
    */
  def countTokens(parseSource: PolytreeParseSource, excludePunctuation: Boolean): Int = {
    (parseSource.parseIterator map { parse =>
      if (excludePunctuation) {
        parse.tokens.tail count { tok => tok.getDeterministicProperty('cpos) != Symbol(".") }
      } else {
        parse.tokens.tail.size
      }
    }).sum
  }
}

object InMemoryPolytreeParseSource {

  /** Create an InMemoryPolytreeParseSource from a filename and a file format.
    *
    * If the dataSource argument is specified as "datastore", it will look for the
    * file in the AI2 private datastore. Otherwise it will look for the file on the local drive.
    *
    * @param filename the name of the file containing the parses
    * @param fileFormat the file format of the parse file
    * @param dataSource where to look for the file ("datastore" for the AI2 datastore,
    * "local" for the local drive)
    * @return the constructed parse source
    */
  def getParseSource(filename: String, fileFormat: PolytreeParseFileFormat,
    dataSource: String = "local"): PolytreeParseSource = {

    val parseFilename: String = dataSource match {
      case "datastore" =>
        val path: java.nio.file.Path =
          Datastore("private").directoryPath(
            "org.allenai.corpora.parsing",
            "treebanks",
            1
          )
        Paths.get(path.toString, filename).toString
      case _ =>
        filename
    }
    InMemoryPolytreeParseSource(PolytreeParse.fromFile(parseFilename, fileFormat).toIterable)
  }
}

/** Creates a data source from a file of parse trees.
  *
  * @param filename the file containing the parse trees
  * @param format the file format
  */
case class FileBasedPolytreeParseSource(
    filename: String,
    format: PolytreeParseFileFormat
) extends PolytreeParseSource {

  override def parseIterator: Iterator[PolytreeParse] = {
    PolytreeParse.fromFile(filename, format)
  }

  override def sentenceIterator: Iterator[Sentence] = {
    PolytreeParse.fromFile(filename, format) map { _.sentence }
  }
}

object FileBasedPolytreeParseSource {

  /** Create a FileBasedPolytreeParseSource from a filename and a file format.
    *
    * If the dataSource argument is specified as "datastore", it will look for the
    * file in the AI2 private datastore. Otherwise it will look for the file on the local drive.
    *
    * @param filename the name of the file containing the parses
    * @param fileFormat the file format of the parse file
    * @param dataSource where to look for the file ("datastore" for the AI2 datastore,
    * "local" for the local drive). Supports datastore parameters in the format
    * "datastore/<group>/<name>/<version>", where any of <group>, <name>, <version> can
    * be left empty and default to "org.allenai.corpora.parsing", "treebanks", respectively "1".
    * @return the constructed parse source
    */
  def getParseSource(filename: String, fileFormat: PolytreeParseFileFormat,
    dataSource: String = "local"): PolytreeParseSource = {

    val datastoreRegex = "datastore/(.+?)/(.+?)/(.+?)".r
    val defaultGroup = "org.allenai.corpora.parsing"
    val defaultName = "treebanks"
    val defaultVersion = "1"
    val parseFilename: String = dataSource match {
      case "datastore" =>
        val path: java.nio.file.Path =
          Datastore("private").directoryPath(
            defaultGroup,
            defaultName,
            defaultVersion.toInt
          )
        Paths.get(path.toString, filename).toString
      case datastoreRegex(group, name, version) =>
        def getOrElse(s: String, default: String) = {
          Option(s).filter(_.nonEmpty).getOrElse(default)
        }
        val path: java.nio.file.Path =
          Datastore("private").directoryPath(
            getOrElse(group, defaultGroup),
            getOrElse(name, defaultName),
            getOrElse(version, defaultVersion).toInt
          )
        Paths.get(path.toString, filename).toString
      case "local" =>
        filename
      case _ => throw new Exception(s"bad dataSource: $dataSource")
    }
    FileBasedPolytreeParseSource(parseFilename, fileFormat)
  }
}

case class MultiPolytreeParseSource(parseSources: Iterable[PolytreeParseSource])
    extends PolytreeParseSource {

  override def parseIterator: Iterator[PolytreeParse] = {
    (parseSources map { _.parseIterator }) reduce { (x, y) => x ++ y }
  }

  override def sentenceIterator: Iterator[Sentence] = {
    (parseSources map { _.sentenceIterator }) reduce { (x, y) => x ++ y }
  }
}

sealed abstract class PolytreeParseFileFormat
case class ConllX(useGoldPOSTags: Boolean, makePoly: Boolean = false)
  extends PolytreeParseFileFormat
