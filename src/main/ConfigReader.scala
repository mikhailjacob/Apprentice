package main
import data._
import parse._
import graph._
import java.io._
import java.util.Properties
import scala.collection.mutable.HashMap

class ConfigReader(val configFile: String) {

  val properties = new Properties()
  val in = new FileInputStream(configFile)
  properties.load(in)
  in.close()
  println("Parameters supplied: " + properties.getProperty("parameters"))
  val paraNames = properties.getProperty("parameters").split(",")

  def allParameters(): Array[Properties] =
    {
      var params: Array[Properties] = new Array[Properties](0)
      for (n <- paraNames) {
        val name = n.trim
        // for each parameter listed, we get a list of values
        val values = properties.getProperty(name).split(",")
        if (params.isEmpty) {
          params = values.map { v =>
            val p = new Properties()
            p.setProperty(name, v)
            p
          }
        }
        else {
          // for each existing Properties object, we append this parameter
          params = params flatMap { param =>
            values.map { v =>
              val p = param.clone().asInstanceOf[Properties]
              p.setProperty(name, v)
              p
            }
          }
        }
      }

      params
    }

  def initData(): (List[Story], List[Cluster]) =
    {
      val storyFile = properties.getProperty("storyFile")
      val clusterFile = properties.getProperty("clusterFile")

      println("using story file: " + storyFile)
      var storyList: List[Story] = GoldParser.parseStories(storyFile)

      println("using cluster file: " + clusterFile)
      val clusterList: List[Cluster] = initClusters(storyList, clusterFile)

      //storyList = filterUnused(storyList, clusterList)
      (storyList, clusterList)
    }

  def filterUnused(storyList: List[Story], clusterList: List[Cluster]): List[Story] =
    {
      val used = clusterList.flatMap { _.members }
      storyList map { story =>
        val newMembers = story.members.filter { s => used.contains(s) }
        val str = story.members.filterNot{s => used.contains(s)}.map{_.toShortString()}.mkString("\n")
        println(str)
        new Story(newMembers)
      }
    }
  /** initializing the clusters. assigning the sentences to the right story and cluster, so we
   *  do not create duplicate sentence objects.
   *
   */
  def initClusters(storyList: List[Story], clusterFile: String) =
    {
      val hashmap = new HashMap[Int, Sentence]
      storyList foreach {
        story =>
          story.members foreach
            {
              sentence =>
                if (hashmap.contains(sentence.id)) throw new ParsingException("sentence repeated" + sentence.id)
                hashmap += ((sentence.id, sentence))
            }
      }

      GoldParser.parseClusters(clusterFile) map {
        c =>
          val newMembers = c.members map
            {
              sentence =>
                // make sure we get the same sentence 
                hashmap.get(sentence.id).get
            }
          val newC = new Cluster(c.name, newMembers)
          newC.members foreach { s =>
            s.cluster = newC
          }
          newC
      }
    }

  def printParameterNames(pw: PrintWriter) {
    paraNames foreach { name =>
      pw.print(name.trim + ", ")
    }
    pw.println()
  }

  def printParameterValues(para: Properties, pw: PrintWriter) {
    paraNames foreach { name =>
      val value = para.getProperty(name.trim)
      pw.print(value + ", ")
    }
  }
}

object ConfigReader {
  def main(args: Array[String]) {
    val reader = new ConfigReader("configRt1.txt")
    val (stories, clusters) = reader.initData()
    for(s <- stories) println(s)
    val parameters = reader.allParameters()
    val outputPath = new File(reader.properties.getProperty("storyFile")).getParent();
    var i = 1;
    val pw = new PrintWriter(new BufferedOutputStream(new FileOutputStream(outputPath + "\\summary.csv")));

    reader.printParameterNames(pw)

    parameters foreach { para =>
      reader.printParameterValues(para, pw)
      para.put("outputFile", outputPath + "\\conf" + i)
      println(outputPath + "conf" + i)
      i += 1

      Relation.init(para)
      val gen = new GraphGenerator(stories, clusters, para)
      val (prevErr, prevFreedom, afterErr, afterFreedom) = gen.generate()
      pw.print(prevErr + ", " + afterErr + ", " + prevFreedom + ", " + afterFreedom + ", " + "\n")

    }

    pw.close()
    Thread.sleep(2000)
  }

}