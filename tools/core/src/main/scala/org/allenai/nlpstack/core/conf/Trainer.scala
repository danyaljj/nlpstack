package org.allenai.nlpstack.core.conf

abstract class Trainer[E, V](val features: FeatureSet[E, V]) {
  val apply = train _
  def train(examples: Iterable[Labelled[E]]): Function[E, V]
}
