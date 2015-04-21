package org.allenai.nlpstack.parse.poly.ml

import org.allenai.common.json._
import org.allenai.nlpstack.parse.poly.decisiontree.{
  FeatureVector => DTFeatureVector,
  ProbabilisticClassifierTrainer,
  JustifyingProbabilisticClassifierTrainer,
  InMemoryFeatureVectorSource,
  SparseVector,
  ProbabilisticClassifier,
  JustifyingProbabilisticClassifier,
  Justification,
  DecisionTreeJustification,
  RandomForestJustification
}
import org.allenai.nlpstack.parse.poly.fsm.SimpleTask
import reming.DefaultJsonProtocol._
import scala.collection.immutable.HashSet

/** A WrapperClassifier wraps a ProbabilisticClassifier (which uses integer-based feature
  * names) in an interface that allows you to use the more natural
  * org.allenai.nlpstack.parse.poly.ml FeatureVector format for classification.
  * This is a trait that specific wrappers can extend.
  */
case class WrapperClassifier {
  def classifier(): ProbabilisticClassifier
  def featureNameMap(): Seq[(Int, FeatureName)]

  /** The inverse of featureNameMap. */
  @transient
  protected val featureNameToIndex: Map[FeatureName, Int] =
    (featureNameMap map {
      case (featIndex, feat) =>
        (feat, featIndex)
    }).toMap

  /** Returns a feature name based on an index. */
  protected def featureNameForIndex(index: Int): FeatureName = {
    val map = featureNameMap.toMap
    map(index)
  }

  /** Returns the most probable (integer) outcome, given the input feature vector.
    *
    * @param featureVector the feature vector to classify
    * @return the most probable (integer) outcome
    */
  def classify(featureVector: FeatureVector): Int = {
    classifier.classify(
      WrapperClassifier.createDTFeatureVector(featureVector, featureNameToIndex, None)
    )
  }

  /** Returns a distribution over all (integer) outcomes, given the input feature vector.
    *
    * @param featureVector the feature vector to classify
    * @return the most probable (integer) outcome
    */
  def getDistribution(featureVector: FeatureVector): Map[Int, Float] = {
    classifier.outcomeDistribution(
      WrapperClassifier.createDTFeatureVector(featureVector, featureNameToIndex, None)
    )
  }

  /** Given the input feature vector, returns the most probable (integer) outcome, along
    * with a justification for the outcome.
    *
    * @param featureVector the feature vector to classify
    * @return a tuple containing the most probable (integer) outcome and the justification
    * (a text explanation) for that outcome.
    */
  def classifyAndJustify(featureVector: FeatureVector): (Int, String) = {
    (classifier.classifyAndJustify(
      WrapperClassifier.createDTFeatureVector(featureVector, featureNameToIndex, None)
    )) match {
        case (outcome: Int, justification: DecisionTreeJustification) =>
          (outcome, prettyPrintDecisionTreeJustification(justification))
        case (outcome: Int, justification: RandomForestJustification) =>
          (outcome, prettyPrintRandomForestJustification(justification))
        case (outcome: Int, _) => (outcome, "")
      }
  }

  /** Returns a distribution over all (integer) outcomes, given the input feature vector.
    *
    * @param featureVector the feature vector to classify
    * @return a tuple with the most probable (integer) outcome and the classifier justification for
    * the outcome
    */
  def getDistributionWithJustification(
    featureVector: FeatureVector
  ): Map[Int, (Float, Justification)] = {
    classifier.outcomeDistributionWithJustification(
      WrapperClassifier.createDTFeatureVector(featureVector, featureNameToIndex, None)
    )
  }

  /** Takes a Decision Tree Justification and turns it into a map of feature-value pairs by
    * mapping feature indexes to respective (String) names based on the featureNameMap
    * field.
    */
  def getExplainableDecisionTreeJustification(
    justification: DecisionTreeJustification
  ): Map[FeatureName, Int] = {
    // Map the feature indexes to their feature names
    (justification.breadCrumb.map {
      case (featureNameIx: Int, featureVal: Int) =>
        (featureNameForIndex(featureNameIx), featureVal)
    }).toMap
  }

  /** Takes a Random Forest Justification and turns it into a seq of explainable decision tree
    * justifications, as defined in getExplainableDecisionTreeJustification-- there is an
    * explainable decision tree justification for each decision tree in the random forest that
    * voted for the chosen outcome.
    */
  def getExplainableRandomForestJustification(
    justification: RandomForestJustification
  ): Seq[Map[FeatureName, Int]] = {
    justification.decisionTreeJustifications map { dtJustification =>
      getExplainableDecisionTreeJustification(dtJustification)
    }
  }

  /** Stringifies a Decision Tree justification into a set of feature-value pairs corresponding
    * to every decision in the breadcrumb that led to a classification outcome.
    */
  def prettyPrintDecisionTreeJustification(
    justification: DecisionTreeJustification
  ): String = {
    // Map the feature indexes to their feature names
    val featureMap = getExplainableDecisionTreeJustification(justification)
    "[ " + (for {
      (featureName, featureVal) <- featureMap
    } yield {
      featureName + " = " + featureVal
    }).mkString(", ") + " ]"
  }

  /** Stringifies a Random Forest justification.
    */
  def prettyPrintRandomForestJustification(
    justification: RandomForestJustification
  ): String = {
    s"\nRandom Forest with ${justification.totalDecisionTreeCount} trees, of which " +
      s"${justification.decisionTreeCountForOutcome} trees voted for the current outcome\n" +
      s"Top ${justification.decisionTreeJustifications.size} decision tree justification(s):\n" +
      "[\n" + justification.decisionTreeJustifications.map(j =>
        prettyPrintDecisionTreeJustification(j)).mkString(",\n\n") + "\n]\n"
  }

  /** Stringifies a classifier's justification.
    */
  /*
  def prettyPrintJustification(justification: Justification): String = {
    justification match {
      case dtJustification: DecisionTreeJustification =>
        prettyPrintDecisionTreeJustification(dtJustification)
      case rfJustification: RandomForestJustification =>
        prettyPrintRandomForestJustification(rfJustification)
      case _ => ""
    }
  }
  */

}

/** Provide Serialization and Deserialization methods based on the runtime type of
  * WrapperClassifier.
  */
object WrapperClassifier {
  implicit val basicWCformat = jsonFormat2(BasicWrapperClassifier.apply)
  implicit val justifyingWCformat = jsonFormat2(JustifyingWrapperClassifier.apply)
  implicit val wcJsonFormat = parentFormat[WrapperClassifier](
    childFormat[BasicWrapperClassifier, WrapperClassifier],
    childFormat[JustifyingWrapperClassifier, WrapperClassifier]
  )

  /** Converts a string-based feature vector into an integer-based feature vector.
    *
    * @param featureVector the string-based feature vector
    * @param featureNameToIndex a map from string-based feature names to integers
    * @param outcome the outcome (if known) of the feature vector
    * @return the equivalent integer-based feature vector
    */
  def createDTFeatureVector(
    featureVector: FeatureVector,
    featureNameToIndex: Map[FeatureName, Int],
    outcome: Option[Int]
  ): DTFeatureVector = {
    val trueAttributeNames: Seq[FeatureName] =
      featureVector.values filter { _._2 != 0 } map { _._1 }
    val trueAttributes: HashSet[Int] =
      HashSet(trueAttributeNames
        .filter(featureNameToIndex.contains)
        .map(featureNameToIndex).toSeq: _*)
    new SparseVector(outcome, featureNameToIndex.values.max + 1, trueAttributes)
  }

}

/** Trains a WrapperClassifier from training data.
  *
  * @param classifierTrainer the underlying trainer to use (from the decisiontree package)
  */
class WrapperClassifierTrainer(
    classifierTrainer: ProbabilisticClassifierTrainer
) {

  def apply(trainingData: TrainingData): WrapperClassifier = {
    val featureNames: Seq[FeatureName] = trainingData.featureNames.toSeq
    val featureNameToIndex: Map[FeatureName, Int] = featureNames.zipWithIndex.toMap
    val vectors = new InMemoryFeatureVectorSource(
      (trainingData.labeledVectors map {
      case (vec, outcome) =>
        WrapperClassifier.createDTFeatureVector(vec, featureNameToIndex, Some(outcome))
    }).toIndexedSeq,
      SimpleTask("basic")
    )
    println(s"Task has ${vectors.numVectors} training vectors")
    val inducedClassifier: ProbabilisticClassifier = classifierTrainer(vectors)
    val featureMap: Seq[(Int, FeatureName)] =
      featureNames.zipWithIndex filter {
        case (_, featIndex) =>
          inducedClassifier.allFeatures.contains(featIndex)
      } map {
        _.swap
      }
    inducedClassifier match {
      case c: JustifyingProbabilisticClassifier =>
        new JustifyingWrapperClassifier(
          inducedClassifier.asInstanceOf[JustifyingProbabilisticClassifier], featureMap
        )
      case _ =>
        BasicWrapperClassifier(inducedClassifier, featureMap)

    }
  }
}
