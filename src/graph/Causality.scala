package graph

import data._
import parse._
import javaquine.Term
import javaquine.Formula
//import utils._
import scala.collection.mutable.HashMap
import scala.collection.mutable.ListBuffer
import scala.collection.JavaConversions._

object Causality {

  val MAX_OR = 2

  def findCausal(storyList: List[Story], graph: Graph): Graph = {
    val usedClusters = graph.usedClusters()
    val total = usedClusters.length

    // a cluster and its possible predecessors
    val combinations = graph.links.groupBy { l => l.target }.map {
      case (cluster, preLinks) =>
        val predecessors = preLinks.map { _.source }.distinct
        (cluster, predecessors)
    }

    //    println(combinations.map {
    //      case (cluster, predecessors) => cluster.name + " : " + predecessors.map(_.name).mkString("; ")
    //    }.mkString("\n"))

    // create the probability object
    val prob = new EventProbability(storyList, usedClusters)
    // contains a cluster and its causal predecessors
    var rawCausal = new HashMap[Cluster, List[List[Cluster]]]()

    for (pair <- combinations) {
      var c = pair._1
      var set = pair._2.toList

      // MAX_OR is the maximum number of elements OR-ed together
      for (i <- 1 to MAX_OR) {

        val candidates = set.combinations(i)
        //Combinatorics.combinations(i, set)

        candidates foreach { can =>
          // (A or B) -> C
          val AorBList = can.toList
          // computing P(A or B | C)
          // all stories containing C
          val storyC = ANDStories(List(c), storyList)
          // all stories containing A or B
          val storyAB = ORStories(AorBList, storyList)
          val prob1 = storyAB.intersect(storyC).length / storyC.length.toDouble

          // computing P(not C | not (A or B))
          val storyNotC = ANDNotStories(List(c), storyList)
          // computing P(not (A or B))
          val storyNotAB = ORNotStories(AorBList, storyList)
          val prob2 = storyNotAB.intersect(storyNotC).length / storyNotAB.length.toDouble
          //println(" " + pnA + " " + pnAB)
          if (prob2 > 0.9 && prob1 > 0.9 && storyNotAB.length > 3 && storyC.length > 3) {
            // a better explanation exists if part of the list is already causally responsible for the causal successor 
            val betterExplanations = rawCausal.getOrElse(c, Nil)
            if (!betterExplanations.exists(x => x.filterNot(AorBList.contains(_)) isEmpty)) // no better explanation exists
            {
              //if (c.name == "use bathroom")
              println("possible causal: " + AorBList.map(_.name).mkString(" || ") + " ->, " + c.name + ", " + prob1 + ", " + prob2)
              rawCausal += (c -> (AorBList :: betterExplanations))
            }
          }
        }

        // this step simplifies the boolean formula.
        // Currently it only works when MAX_OR = 2

        //        result.get(c) match {
        //          case Some(list) =>
        //            // causal predecessors that appear in a list with two elements OR-ed together
        //            val predecessors = list.flatMap(x => if (x.size == 2) x else Nil).distinct
        //            for (p <- predecessors) {
        //              if (list.count(x => x.contains(p)) >= 2) {
        //                println("multiples")
        //
        //                val pairs = list.filter(x => x.contains(p)) // List of pairs that contains p
        //
        //                val con = pairs.flatMap(x => x) - p // List of clusters that each is OR-ed with p
        //                print(con map (_.name))
        //
        //                val cStories = ORStories(List(c), storyList) // stories containing c
        //
        //                val s = ORNotStories(List(p), ANDStories(con, cStories)) // stories containing c and everything in con but not p
        //                print(" : " + s.size)
        //                val s2 = ORNotStories(con, ANDStories(List(p), cStories)) // stories containing c and p, but not everything in con
        //                println(" reverse : " + s2.size)
        //                if (s.size < storyList.size * 0.1 && s.size < s2.size) {
        //                  val finalList = List(p) :: (list -- pairs)
        //                  println("List changed to " + finalList.map(_.map(_.name).mkString(" || ") + " -> " + c.name).mkString(" \n"))
        //                  result += (c -> finalList)
        //                } else if (s2.size < storyList.size * 0.1 && s2.size < s.size) {
        //                  val finalList = con.map(List(_)) ::: (list -- pairs)
        //                  println("List changed to " + finalList.map(_.map(_.name).mkString(" || ") + " -> " + c.name).mkString(" \n"))
        //                  result += (c -> finalList)
        //                } else if (s2.size < storyList.size * 0.1 && s2.size == s.size) {
        //                  // the shorter one has preference when both choices are equally good
        //                  val finalList = List(p) :: (list -- pairs)
        //                  println("List changed to " + finalList.map(_.map(_.name).mkString(" || ") + " -> " + c.name).mkString(" \n"))
        //                  result += (c -> finalList)
        //                }
        //              }
        //            }
        //          case None =>
        //        }
      }
    }

    /* we have located all causalities. I have moved the simplification here because the
     * the Quine-McCluskey algorithm can handle terms with different length
     * the new simplification method using the Quine-McCluskey algorithm begins here
     */
    val realCausal = new HashMap[Cluster, List[List[Cluster]]]()

    rawCausal foreach {
      case (causee, causers) =>
        // the causee is a single cluster / event that is caused by the causers list
        // the causers are a list of lists. The lists are AND-ed together, and elements in each list are OR-ed
        // to use the QM algorithm, we first turn them into a list of list OR-ed together    
        var formList = List[List[Cluster]]()
        for (causer <- causers) {
          if (formList.isEmpty) {
            // for each element in one list, create a list
            formList = causer.map(List(_))
          } else {
            var newFormList = ListBuffer[List[Cluster]]()
            // this is multiplication
            // we add one element to the end of each term
            for (element <- causer; prevList <- formList) {
              newFormList += element :: prevList
            }
            formList = newFormList.toList
          }
        }

        // now we have a boolean formula before the simplification
        var length = 0
        var afterLength = formList.size
        var formula = formList
        if (afterLength > 1) {
          // we record the the length before, and compare with the length after simplification.
          // if they stop changing, the simplification has converged.
          do {
            length = afterLength
            formula = simplifyFormula(formula)
            //println("after simplification, before reduction")
            //printFormula(formula, causee)
            formula = discardSmallTerms(formula, storyList)
            afterLength = formula.size
          } while (afterLength != length)
        }

        realCausal += (causee -> formula)

        printFormula(formula, causee)
        println("\n")
        //println(" -> " + causee.name)
      /* The end of simplification */
    }

    var causalLinks = realCausal.flatMap {
      case (causee, causers) =>
        causers.flatMap { _.map { x => new Link(x, causee, "C") } }
    }.toList

    causalLinks = causalLinks.distinct

//    for (i <- 0 until causalLinks.length; j <- i + 1 until causalLinks.length if causalLinks(i) == causalLinks(j))
//      println(i + " " + j + " " + causalLinks(i) + "   " + causalLinks(j))

    //println(causalLinks.map(x => x.toString + " " + x.kind).mkString("\n"))

    new Graph(graph.nodes, causalLinks ::: graph.links)
  }

  private def printFormula(formula: List[List[Cluster]], causee: Cluster) {
    var text = formula.map { _.map(_.name).mkString("( ", " * ", " )") }.mkString(" + ")
    text += " -> " + causee.name
    println(text)
  }

  //  /** find stories that do not contain anything from the clusterlist
  //   * 
  //   */
  //  def NotStories(clusters: List[Cluster], storyList: List[Story]): List[Story] =
  //    {
  //      storyList.filter {
  //        story =>
  //          clusters exists {
  //            cluster => story.members exists { x => cluster.members.contains(x) }
  //          }
  //      }
  //    }

  /**
   * Simplifies a boolean formula using the Quine-McCluskey algorithm.
   * Returns a new formula after simplification
   *
   * @argument formulaList
   * It is a list of list of clusters. The clusters in the inner list are multiplied together.
   * Elements in the outer list are summed together.
   *
   */
  private def simplifyFormula(formulaList: List[List[Cluster]]): List[List[Cluster]] = {

    // eliminate repeated elements
    val formList = formulaList.map(_.distinct)

    // assign an index to each cluster
    val cl2Num = new HashMap[Cluster, Int]()
    val num2Cl = new HashMap[Int, Cluster]()
    var max = 0
    formList.flatMap { x => x }.distinct.foreach { cl =>
      cl2Num += (cl -> max)
      num2Cl += (max -> cl)
      max += 1
    }

    val terms = formList.map { form =>
      val array = Array.fill[Byte](max)(2) // 2 is the dont care for the term class
      for (cl <- form) {
        array(cl2Num(cl)) = 1
      }
      new Term(array)
    }

    var formula = ListBuffer[List[Cluster]]()
    // calling the QM algorithm
    val f = new Formula(terms)
    f.reduceToPrimeImplicants()
    f.reducePrimeImplicantsToSubset()
    val reduced = f.getTerms()

    reduced foreach { term =>
      val arr = term.getVals()

      var multiplyList = List[Cluster]()

      for (i <- 0 until max) {
        if (arr(i) == 1) {
          val c = num2Cl(i)
          multiplyList = c :: multiplyList

        } else if (arr(i) == 0) { // currently we do not have zeros
        }
      }
      formula += multiplyList
    }

    formula.toList
  }

  /**
   * From a boolean formula (AB + CD + EFG + H), this function discards terms that add too few stories
   *  to matter.
   * Returns the result formula
   *
   *
   */
  private def discardSmallTerms(formula: List[List[Cluster]], storyList: List[Story]): List[List[Cluster]] = {

    // this list contains the final formula
    // each list of clusters is a term in the formula, i.e. a number of elements AND-ed together
    // the list of stories that follows is the list of stories that satisfy this term
    // i.e. the stories that contain all these clusters
    var supportedFormula = ListBuffer[(List[Cluster], List[Story])]()
    var supportingStories = ListBuffer[Story]()

    for (item <- formula) {
      val ands = ANDStories(item, storyList)
      // now we add this to the final formula
      supportingStories ++= ands
      supportedFormula += ((item, ands))
    }

    /* sorting first according to the size of elements ANDed together
     * and then the number of supporting stories
     */

    supportedFormula = supportedFormula.sortWith {
      (x, y) =>
        if (x._1.size < y._1.size) true
        else if (x._1.size == y._1.size) {
          if (x._2.size > y._2.size) true
          else false
        } else false
    }

    supportingStories = supportingStories.distinct
    //println("supporting count = " + supportingStories.size)

    val answer = ListBuffer[List[Cluster]]()

    val total: Double = storyList.size //supportingStories.size
    for (pair <- supportedFormula) {
      //println("sheer size =" + (pair._2 intersect supportingStories).size)
      val contribution = (pair._2 intersect supportingStories).size / total
      //println("contribution = " + contribution)
      if (contribution > 0.05) {
        //println(pair._1.map(_.name).mkString(" * ") + " + ")
        answer += pair._1
      } else {
        println("omitted: " + pair._1.map(_.name).mkString(" * ") + " + ")
      }
      supportingStories --= pair._2
      //println("supporting count = " + supportingStories.size)
    }
    //println(" = " + causee.name + "\n\n")

    answer.toList
  }

  /**
   * given a list of clusters (A, B, ...), find the number of stories that contains sentences in A or B or ...
   *
   */
  def ORStories(clusters: List[Cluster], storyList: List[Story]): List[Story] =
    {
      storyList.filter {
        story =>
          clusters exists {
            cluster => story.members exists { x => cluster.members.contains(x) }
          }
      }
    }

  /**
   * given a list of clusters (A, B, ...), find the number of stories that contains no sentences in A and B and ...
   *
   */
  def ORNotStories(clusters: List[Cluster], storyList: List[Story]): List[Story] =
    {

      var answer: List[Story] = storyList

      for (cluster <- clusters) {
        answer = storyList.filterNot { s => s.members.exists(cluster.members.contains(_)) }
      }

      answer
    }

  /**
   * given a list of clusters (A, B, ...), find the number of stories that contains sentences in A and sentences in B and ...
   *
   */
  def ANDStories(clusters: List[Cluster], storyList: List[Story]): List[Story] =
    {
      storyList.filter { story =>
        clusters.forall {
          cluster => story.members.exists { x => cluster.members.contains(x) }
        }
      }
    }

  /**
   * given a list of clusters (A, B, ...), find the number of stories that contains no sentences in A or B or ...
   *
   */
  def ANDNotStories(clusters: List[Cluster], storyList: List[Story]): List[Story] =
    {
      storyList.filterNot { story =>
        clusters.exists {
          cluster => story.members.exists { x => cluster.members.contains(x) }
        }
      }
    }
}

class EventProbability(val stories: List[Story], val clusters: List[Cluster]) {

  val storySize: Double = stories.size

  /**
   * returns P(not A)
   *
   */
  def singleProb(cluster: Cluster): Double = {
    val count = stories.filter(s => s.members.exists(cluster.members.contains(_))).size
    count / storySize
  }

  /**
   * returns P(A and B and C and...)
   *  To computer P((not A) and (not B) and ...), use 1 - P(A or B or...)
   */
  def andProb(clusters: List[Cluster]): Double = {
    val count = stories.filter { story =>
      // for each of the clusters
      // this story should contain one sentence from it
      clusters.forall { cluster =>
        story.members.exists(cluster.members.contains(_))
      }
    }.size

    count / storySize
  }

  def orProb(clusters: List[Cluster]): Double = {
    val count = stories.filter { story =>
      // for one of the clusters
      // this story should contain one sentence from it
      clusters.exists { cluster =>
        story.members.exists(cluster.members.contains(_))
      }
    }.size

    count / storySize
  }

  def notProb(cluster: Cluster): Double = 1 - singleProb(cluster)

}