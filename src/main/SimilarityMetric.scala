package main
import data._
import utils.HungarianAlgo
import scala.collection.mutable.Queue
import scala.collection.mutable.HashMap
import de.linguatools.disco.DISCO
import de.linguatools.disco.ReturnDataBN
import de.linguatools.disco.ReturnDataCol

class SimilarityMetric {

  val discoDir = "../../en-wikipedia-20080101/"
  val disco = new DISCO(discoDir, false);

  val simHash = HashMap.empty[(String, String), Double]

  def wordSimilarity(word1: Token, word2: Token): Double =
    {
      if (word1 == word2) 1
      else {
        val lemma1 = word1.lemma
        val lemma2 = word2.lemma
        val order1 = simHash.get(lemma1, lemma2)
        if (order1.isDefined) return order1.get
        else {
          val order2 = simHash.get(lemma2, lemma1)
          if (order2.isDefined) return order2.get
        }

        val value = disco.firstOrderSimilarity(lemma1, lemma2)
        simHash.put((lemma1, lemma2), value)
        value
      }
    }

  def dependencySimilarity(dep1: Dependency, dep2: Dependency): Double =
    {
      if (dep1.longName != dep2.longName) 0
      else {
        val head1 = dep1.gov.lemma
        val head2 = dep2.gov.lemma

        val tail1 = dep1.dep.lemma
        val tail2 = dep2.dep.lemma

        if (head1 == head2 && ((tail1 == "Sally" && tail2 == "John") || (tail2 == "Sally" && tail1 == "John")))
          return 1
        else {
          val govSim = wordSimilarity(dep1.gov, dep2.gov)
          val depSim = wordSimilarity(dep1.dep, dep2.dep)
          var base = 0.5 * depSim + 0.5 * govSim

//          if (head1 != head2 && tail1 == tail2) {
//            val x = existsCollocation(head1, tail1)
//            val y = existsCollocation(head2, tail1)
//            if (x.isDefined && y.isDefined) {
//              val common = (x.get + y.get) / 2
//              val bonus = 0.3 * govSim * common // scale this bonus with the original similarity of the governor words
//              base += bonus
//              println(head1 + " and " + head2 + " have common context: " + tail1 + " adding " + bonus)
//            }
//          } else if (head1 == head2 && tail1 != tail2) {
//            val x = existsCollocation(tail1, head1)
//            val y = existsCollocation(tail2, head1)
//            if (x.isDefined && y.isDefined) {
//              val common = (x.get + y.get) / 2
//              val bonus = 0.3 * depSim * common // scale this bonus with the original similarity of the dependent words
//              base += bonus
//              println(tail1 + " and " + tail2 + " have common context: " + head1 + " adding " + bonus)
//            }
//          }

          base
        }
      }
    }

  private def existsCollocation(mainWord: String, word2: String): Option[Double] = {
    val collocation1 = disco.collocations(discoDir, mainWord)
    if (collocation1 != null)
      collocation1.find { _.word == word2 }.map { _.value.toDouble }
    else
      None
  }

  /**
   * return (Similarity, Dissimilarity). Sentence similarity is a maximum flow problem.
   * We implement the Ford-Fulkerson's Algorithm
   *
   */
  def sentenceSimilarity(sent1: Sentence, sent2: Sentence): (Double, Double) =
    {
      //println("comparing sentences: " + sent1.id + " " + sent2.id)

      var deps1 = sent1.deps
      var deps2 = sent2.deps
      // deps1.length must be less than deps2.length
      // swap them if necessary
      if (deps1.length > deps2.length) {
        val temp = deps2
        deps2 = deps1
        deps1 = temp
      }
      //println("processsed: deps1 " + deps1.length + ". deps2 " + deps2.length)
      // this adjacency matrix represents the residual graph
      var residual = Array.fill(deps1.length, deps2.length)(0.0)
      //      // this is the current flow we have
      //      var flow = Array.fill(deps1.length, deps2.length)(0.0)

      // initializing the graph
      for (i <- 0 to deps1.length - 1) {
        for (j <- 0 to deps2.length - 1) {
          val sim = dependencySimilarity(deps1(i), deps2(j))
          residual(i)(j) = sim
          //residual(j)(i) = sim
          //println(i + "," + j + ": " + sim)
        }
      }
      val result = HungarianAlgo.hgAlgorithm(residual, "max");

      var sum: Double = 0

      for (i <- 0 to result.length - 1) {
        val idx1 = result(i)(0)
        val idx2 = result(i)(1)
        // the scaling factor = e ^ -0.2x
        sum += residual(idx1)(idx2) * math.pow(math.E, (deps1(idx1).depth + deps2(idx2).depth) / -30)
      }
      
      //println("loc1 : " + sent1.location + " loc 2: " + sent2.location)
      val location = 0.3 - 0.6 * math.abs(sent1.location - sent2.location)
      
      if (sum < 0.6) sum = 0
      sum += location 
      if (sum < 0) sum = 0

      (sum, 0)

    }

  def preprocess(deps: List[Dependency]): List[Dependency] =
    {
      var result = deps
      val suspect = deps.filter { d =>
        d.dep.word == "John" && d.relation == "nsubj"
      }.filter { dep1 => deps.exists { dep2 => dep2.gov == dep1.gov && dep2.dep.word == "Sally" && dep2.relation == "nsubj" } }

      if (suspect != Nil) {
        // remove sally's dependencies
        suspect.foreach { d =>
          result = result.filterNot(dr => dr.gov == d.gov && dr.dep.word == "Sally")
        }
        // remove anything between john and sally
        result = result.filterNot(dr => (dr.gov.word == "John" && dr.dep.word == "Sally") || (dr.gov.word == "Sally" && dr.dep.word == "John"))
        //println("removed things from " + deps + ". Now = " + result)
      }

      result
    }
}