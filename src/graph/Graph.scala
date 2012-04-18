package graph

import data._
import java.io._
import scala.collection.immutable.HashMap

class Graph(val nodes: List[Cluster], val links: List[Link]) extends XStreamable {

  // this is used in XStreamable
  override def alias() = "plot-graph"

  def causalLinks() = links.filter(_.isCausal)
  def temporalLinks() = links.filter(_.isTemporal)
  def usedClusters() = links.flatMap { link => List(link.source, link.target) }.distinct

  /**
   * returns if cluster1 and 2 are ordered on the given graph, which is described by the links
   *
   */
  def ordered(cluster1: Cluster, cluster2: Cluster): Boolean =
    shortestDistance(cluster1, cluster2) != -1 || shortestDistance(cluster2, cluster1) != -1

  /**
   * find the shortest part distance between the two nodes based on the graph
   *  if source cannot be reached from target, return -1
   *
   */
  def shortestDistance(source: Cluster, target: Cluster): Int =
    {
      var debug = false
      //if (source.name == "choose restaurant" && target.name == "eat food") debug = true
      // a breadth-first search
      var longest = -1
      val queue = scala.collection.mutable.Queue[(Cluster, Int)]()
      var remaining = links
      queue += ((source, 0))

      while (queue != Nil) {
        val elem = queue.dequeue()

        val head = elem._1
        val dist = elem._2
        if (debug) println("dequeue: " + head.name + " " + dist)
        if (head == target) {
          return dist
        } else {
          links.filter(link => link.source == head).foreach {
            link =>
              queue.enqueue((link.target, dist + 1))
              if (debug) println("enqueue: " + link.target.name + " " + (dist + 1))
          }
        }
      }
      //println("distance from " + source.name + " to " + target.name + " = " + longest)
      -1
    }

  /**
   * find the diameter between the two nodes based on the graph
   *
   */
  def diameter(source: Cluster, target: Cluster): Int =
    {
      var debug = false
      //if (source.name == "choose restaurant" && target.name == "eat food") debug = true
      // a breadth-first search
      var longest = -1
      val queue = scala.collection.mutable.Queue[(Cluster, Int)]()
      var remaining = links
      queue += ((source, 0))

      while (queue != Nil) {
        val elem = queue.dequeue()

        val head = elem._1
        val dist = elem._2
        if (debug) println("dequeue: " + head.name + " " + dist)
        if (head == target) {
          if (dist > longest) longest = dist
        } else {
          links.filter(link => link.source == head).foreach {
            link =>
              queue.enqueue((link.target, dist + 1))
              if (debug) println("enqueue: " + link.target.name + " " + (dist + 1))
          }
        }
      }
      //println("distance from " + source.name + " to " + target.name + " = " + longest)
      longest
    }

  def takeSteps(source: Cluster, steps: Int): List[Cluster] =
    {
      var ends = List[Cluster]()

      val distance = scala.collection.mutable.Queue[(Cluster, Int)]()
      var remaining = links
      distance += ((source, 0))

      while (distance != Nil) {
        val elem = distance.dequeue()
        val head = elem._1
        val dist = elem._2
        if (dist == steps) ends = head :: ends // find one end. add it to the list
        else {
          links.filter(link => link.source == head).foreach {
            link => distance.enqueue((link.target, dist + 1))
          }
        }
      }

      ends.distinct
    }

  /**
   * eliminates redundant nodes, which do not appear in any links
   *
   */
  def reduce(): Graph =
    {
      val newNodes = usedClusters
      new Graph(newNodes, links)
    }

  /**
   * simplifies the graph to a simple graph. When A->B and B->C are both present, A->C is omitted.
   *  The reduction only applies to links of the same kind
   */
  def simplify(): Graph = {

    // simplifying the graph
    val pairs = (0 until nodes.length) zip nodes
    val num2cluster = HashMap(pairs: _*) // mapping from number to cluster object
    val cluster2num = num2cluster.map(_.swap) // mapping from a cluster object to its number

    val temporals = links filter { _.isTemporal } map {
      link =>
        val id1 = cluster2num(link.source)
        val id2 = cluster2num(link.target)
        (id1, id2)
    }

    val causals = links filter { _.isCausal } map {
      link =>
        val id1 = cluster2num(link.source)
        val id2 = cluster2num(link.target)
        (id1, id2)
    }

    // this is a helper function that delete redundant links
    def reduceLinks = (numbers: List[(Int, Int)]) => {

      if (numbers == Nil) Nil
      else {
        val order = new Ordering(numbers.toSet[(Int, Int)])
        order.necessary()
      }

    }

    val newLinks =
      reduceLinks(temporals).map { l => new Link(num2cluster(l._1), num2cluster(l._2), "T") }.toList ++
        reduceLinks(causals).map { l => new Link(num2cluster(l._1), num2cluster(l._2), "C") }

    new Graph(nodes, newLinks)
  }

  // reduce + simplify
  def compact(): Graph = reduce.simplify

  // replace temporal with causal links so that there is at most one link between two clusters
  def singleLink(): Graph = {
    val causal = causalLinks()
    var temporal = temporalLinks()

    temporal = temporal.filterNot { tl => causal.exists { c => c.target == tl.target && c.source == tl.source } }

    new Graph(nodes, temporal ::: causal)
  }

  /**
   * draws the diagram to disk
   *
   */
  def draw(fn: String) {

    val filename = fn + ".txt"
    val file = new File(filename)
    val writer = new PrintWriter(new BufferedOutputStream(new FileOutputStream(file)))
    writer.println("digraph G {")
    writer.println(temporalLinks.map { l => "\"" + l.source.name + "\" -> \"" + l.target.name + "\" [style = \"dashed\"]" }.mkString("\n"))
    writer.println(causalLinks.map { l => "\"" + l.source.name + "\" -> \"" + l.target.name + "\"" }.mkString("\n"))
    writer.println("}")
    writer.close()
    
//    if (!causalLinks.isEmpty){
//      println("causal links::")
//      for (cl <- causalLinks)
//        println(cl)
//    }

    val lastIndex = if (!filename.contains(".")) filename.length else filename.lastIndexOf(".")
    val outputName = filename.substring(0, lastIndex)
    println("outputname = " + filename + " " + outputName)
    Runtime.getRuntime().exec("dot -Tpng -o" + outputName + ".png " + filename)
    file.deleteOnExit()
  }

}

///**
// * contains graph related algorithms
// *
// */
//object GraphAlgo {

  //  /**
  //   * returns if cluster1 and 2 are ordered on the given graph, which is described by the links
  //   *
  //   */
  //  def ordered(links: List[Link], cluster1: Cluster, cluster2: Cluster): Boolean =
  //    findShortestDistance(links, cluster1, cluster2) != -1 || findShortestDistance(links, cluster2, cluster1) != -1
  //
  //  /**
  //   * find the shortest part distance between the two nodes based on the graph
  //   *  if source cannot be reached from target, return -1
  //   *
  //   */
  //  def findShortestDistance(links: List[Link], source: Cluster, target: Cluster): Int =
  //    {
  //      var debug = false
  //      //if (source.name == "choose restaurant" && target.name == "eat food") debug = true
  //      // a breadth-first search
  //      var longest = -1
  //      val queue = scala.collection.mutable.Queue[(Cluster, Int)]()
  //      var remaining = links
  //      queue += ((source, 0))
  //
  //      while (queue != Nil) {
  //        val elem = queue.dequeue()
  //
  //        val head = elem._1
  //        val dist = elem._2
  //        if (debug) println("dequeue: " + head.name + " " + dist)
  //        if (head == target) {
  //          return dist
  //        } else {
  //          links.filter(link => link.source == head).foreach {
  //            link =>
  //              queue.enqueue((link.target, dist + 1))
  //              if (debug) println("enqueue: " + link.target.name + " " + (dist + 1))
  //          }
  //        }
  //      }
  //      //println("distance from " + source.name + " to " + target.name + " = " + longest)
  //      -1
  //    }
  //
  //  /**
  //   * find the diameter between the two nodes based on the graph
  //   *
  //   */
  //  def findDiameter(links: List[Link], source: Cluster, target: Cluster): Int =
  //    {
  //      var debug = false
  //      //if (source.name == "choose restaurant" && target.name == "eat food") debug = true
  //      // a breadth-first search
  //      var longest = -1
  //      val queue = scala.collection.mutable.Queue[(Cluster, Int)]()
  //      var remaining = links
  //      queue += ((source, 0))
  //
  //      while (queue != Nil) {
  //        val elem = queue.dequeue()
  //
  //        val head = elem._1
  //        val dist = elem._2
  //        if (debug) println("dequeue: " + head.name + " " + dist)
  //        if (head == target) {
  //          if (dist > longest) longest = dist
  //        } else {
  //          links.filter(link => link.source == head).foreach {
  //            link =>
  //              queue.enqueue((link.target, dist + 1))
  //              if (debug) println("enqueue: " + link.target.name + " " + (dist + 1))
  //          }
  //        }
  //      }
  //      //println("distance from " + source.name + " to " + target.name + " = " + longest)
  //      longest
  //    }

//}