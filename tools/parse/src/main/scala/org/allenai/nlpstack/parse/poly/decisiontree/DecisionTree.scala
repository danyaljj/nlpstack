package org.allenai.nlpstack.parse.poly.decisiontree

import scala.annotation.tailrec

import reming.DefaultJsonProtocol._

/** Structure to represent a decision tree's justification for a certain classification outcome.
  * Contains index of the chosen node and the breadcrumb that led to it:
  * (feature index, feature value) tuple at each decision point.
  */
case class DecisionTreeJustification(node: Int) extends Justification

/** A decision made at a decision tree node.
  *
  * @param feature the splitting feature at the node
  * @param featureValue the value assigned to the splitting feature
  */
case class DTDecision(feature: Int, featureValue: Int)

/** The sequence of decisions made in order to arrive at a decision tree node.
  *
  * @param decisions the sequence of decisions
  */
case class DTDecisionPath(decisions: Seq[DTDecision])

/** Immutable decision tree for integer-valued features and outcomes.
  *
  * Each data structure is an indexed sequence of properties. The ith element of each sequence
  * is the property of node i of the decision tree.
  *
  * @param outcomes all possible outcomes for the decision tree
  * @param child stores the children of each node (as a map from feature values to node ids)
  * @param splittingFeature stores the feature that each node splits on; can be None for leaf
  * nodes
  * @param outcomeHistograms for each node, stores a map of outcomes to their frequency of
  * appearance at that node (i.e. how many times a training vector with
  * that outcome makes it to this node during classification)
  */
case class DecisionTree(outcomes: Iterable[Int], child: IndexedSeq[Map[Int, Int]],
  splittingFeature: IndexedSeq[Option[Int]], outcomeHistograms: IndexedSeq[Map[Int, Int]])
    extends ProbabilisticClassifier {

  /** The kth element of this sequence is node k's "decision path", i.e. the sequence
    * of decisions that lead from the root to node k.
    */
  @transient lazy val decisionPaths: IndexedSeq[DTDecisionPath] = {
    var pathMap = Map[Int, Seq[(Int, Int)]]()
    pathMap = pathMap + (0 -> Seq[(Int, Int)]())
    for (decisionTreeNode <- topologicalOrder) {
      val currentPath = pathMap(decisionTreeNode)
      for ((featureValue, decisionTreeChild) <- child(decisionTreeNode)) {
        pathMap = pathMap + (decisionTreeChild ->
          (currentPath :+ Tuple2(splittingFeature(decisionTreeNode).get, featureValue)))
      }
    }
    Range(0, child.size) map { node =>
      DTDecisionPath(pathMap(node) map { decision =>
        DTDecision(decision._1, decision._2)
      })
    }
  }

  // For use in KL Divergence calculation. Calculating Root distribution upfront to avoid having
  // to calculate during every call to getNodeDivergenceScore.
  @transient val outcomeDistributionRoot = ProbabilisticClassifier.normalizeDistribution(
    (outcomeHistograms(0) mapValues { _.toFloat }).toSeq
  ).toMap

  override def classify(featureVector: FeatureVector): (Int, Option[Justification]) = {
    val (distribution, decisionNode) = outcomeDistributionExplicit(featureVector)
    val (bestClass, _) = distribution maxBy { case (_, prob) => prob }
    (bestClass, Some(DecisionTreeJustification(decisionNode)))
  }

  /** Gets a probability distribution over possible outcomes..
    *
    * @param featureVector feature vector to compute the distribution for
    * @return probability distribution of outcomes according to training data
    */
  override def outcomeDistribution(featureVector: FeatureVector): Map[Int, Float] = {
    val (result, _) = outcomeDistributionExplicit(featureVector)
    result
  }

  private def outcomeDistributionExplicit(featureVector: FeatureVector): (Map[Int, Float], Int) = {
    val node = findDecisionPoint(featureVector)
    val priorCounts = outcomes.toList.map(_ -> 1).toMap // add-one smoothing
    val distribution = ProbabilisticClassifier.normalizeDistribution(
      (ProbabilisticClassifier.addMaps(outcomeHistograms(node), priorCounts) mapValues {
      _.toFloat
    }).toSeq
    ).toMap
    (distribution, node)
  }

  def outcomeHistogram(featureVector: FeatureVector): Map[Int, Int] = {
    outcomeHistograms(findDecisionPoint(featureVector))
  }

  /** Get the probability distribution for the various outcomes together with the justification
    * for each.
    */
  /*
  override def outcomeDistributionWithJustification(
    featureVector: FeatureVector
  ): Map[Int, (Float, DecisionTreeJustification)] = {
    val (node, breadCrumb) =
      findDecisionPointWithBreabcrumb(featureVector, 0, Seq.empty[(Int, Int)])
    val priorCounts = outcomes.toList.map(_ -> 1).toMap // add-one smoothing
    (ProbabilisticClassifier.normalizeDistribution(
      (ProbabilisticClassifier.addMaps(outcomeHistograms(node), priorCounts)
      mapValues { _.toFloat }).toSeq
    ) map {
      case (outcome: Int, conf: Float) =>
        (outcome, (conf, new DecisionTreeJustification(breadCrumb, node)))
    }).toMap
  }
  */

  /* Return node divergence against root node. This is calculated as:
   * Total no. of outcomes * KL Divergence for requested node against the root node of the tree.
   */
  def getNodeDivergenceScore(node: Int): Double = {
    val outcomeDistributionThisNode = ProbabilisticClassifier.normalizeDistribution(
      (outcomeHistograms(node) mapValues { _.toFloat }).toSeq
    ).toMap
    val klDivergence = (for {
      (k, q) <- outcomeDistributionRoot
      p <- outcomeDistributionThisNode.get(k)
      if q != 0 && p != 0
    } yield {
      p * math.log(p / q)
    }).sum
    outcomes.size * klDivergence
  }

  /** All features used in the decision tree. */
  override def allFeatures: Set[Int] = splittingFeature.flatten.toSet

  /** The most probable outcome at a specified node of the decision tree. */
  private def mostProbableOutcome(nodeIndex: Int) = {
    (outcomeHistograms(nodeIndex) maxBy {
      _._2
    })._1
  }

  /** From a particular node, chooses the correct child according to the feature vector
    * and the node's splitting feature (if there is one).
    *
    * @param nodeId the id of the node
    * @param featureVector the feature vector
    * @return the node id of the correct child (if there is one)
    */
  protected def selectChild(nodeId: Int, featureVector: FeatureVector): Option[Int] = {
    splittingFeature(nodeId) flatMap { feature =>
      child(nodeId).get(featureVector.getFeature(feature))
    }
  }

  /** Enhanced version of the selectChild method above that returns not only the selected child's
    * node index but also the feature index and value in the path that led to that node.
    * and the node's splitting feature (if there is one).
    *
    * param nodeId the id of the node
    * param featureVector the feature vector
    * @return the node id of the correct child (if there is one), together with a tuple containing
    * the index of the feature and feature value that led to that node from the previous node.
    */
  /*
  protected def getChildWithPathInfo(
    nodeId: Int, featureVector: FeatureVector
  ): Option[(Int, (Int, Int))] = {
    for {
      feature <- splittingFeature(nodeId)
      child <- child(nodeId).get(featureVector.getFeature(feature))
    } yield {
      (child, (feature, featureVector.getFeature(feature)))
    }
  }
  */

  /** Finds the "decision point" of the specified feature vector. This is the node for which no
    * child covers the feature vector.
    *
    * @param featureVector feature vector to classify
    * @return the decision tree node that the feature vector is classified into
    */
  @tailrec protected final def findDecisionPoint(
    featureVector: FeatureVector, nodeId: Int = 0
  ): Int = {
    selectChild(nodeId, featureVector) match {
      case None => nodeId
      case Some(child) => findDecisionPoint(featureVector, child)
    }
  }

  /** Enhanced version of findDecisionPoint that finds both the decision point and the cumulative
    * path all the way from the root that led to this point.
    *
    * param featureVector feature vector to classify
    * @return the decision tree node that the feature vector is classified into
    */
  /*
  @tailrec protected final def findDecisionPointWithBreabcrumb(
    featureVector: FeatureVector, nodeId: Int = 0, breadCrumb: Seq[(Int, Int)]
  ): (Int, Seq[(Int, Int)]) = {
    getChildWithPathInfo(nodeId, featureVector) match {
      case None => (nodeId, breadCrumb)
      case Some((child, path)) =>
        findDecisionPointWithBreabcrumb(featureVector, child, breadCrumb :+ path)
    }
  }
  */

  /** A topological order of the decision tree nodes (where the root is the first node). */
  @transient lazy val topologicalOrder: Seq[Int] = {
    val stack = scala.collection.mutable.Stack[Int]()
    var topoList = Seq[Int]()
    stack.push(0)
    while (stack.nonEmpty) {
      val node = stack.pop()
      topoList = node +: topoList
      for ((_, childId) <- child(node)) {
        stack.push(childId)
      }
    }
    topoList.reverse
  }

  /** Prints the decision tree to stdout. */
  def print(featureNames: Vector[String], outcomeNames: Vector[String], nodeId: Int = 0,
    tabbing: String = ""): Unit = {

    splittingFeature(nodeId) match {
      case Some(feature) =>
        println(tabbing + featureNames(feature))
        child(nodeId).get(1) match {
          case Some(child) =>
            print(featureNames, outcomeNames, child, tabbing + "  ")
          case None => println(tabbing + "  +")
        }
        child(nodeId).get(0) match {
          case Some(child) =>
            print(featureNames, outcomeNames, child, tabbing + "  ")
          case None => println(tabbing + "  -")
        }
      case None =>
        println(tabbing + outcomeNames(mostProbableOutcome(nodeId)))
    }
  }
}

object DecisionTree {
  implicit val decisionTreeFormat = jsonFormat4(DecisionTree.apply)
}
