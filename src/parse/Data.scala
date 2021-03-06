package parse

case class Token(val word: String, val pos: String)

case class Sentence(val id: Int, val content: List[Token], var next: Sentence = null, var cluster: Cluster = null) {
  override def toString(): String =
    {
      "(S" + id + ") " + content.map { t => t.word + "\\" + t.pos }.mkString(" ")
    }

  def toShortString(): String =
    {
      "(S" + id + ") " + content.map { _.word }.mkString(" ")
    }
}

class Cluster(val name: String, val members: List[Sentence]) {
  override def toString(): String =
    {
      "Cluster \"" + name + "\": [" + members.map(_.id).mkString(",") + "]"
    }
}

class Story(val members: Array[Sentence]) {
  override def toString(): String =
    {
      "Story (" + members(0).id + ", " + members.last.id + ")"
    }

}
class ClusterLink(val source: Cluster, val target: Cluster, var count: Int = 0) extends Ordered[ClusterLink] {
  
  override def equals(that: Any): Boolean =
    that match {
      case link: ClusterLink => this.source == link.source && this.target == link.target
      case _ => false
    }
  
  def increment()
  {
    count += 1
  }
  
  override def toString() = source.name + ", " + target.name + ", " + count

  override def compare(that:ClusterLink):Int =
  {
    if (this.count < that.count) 1
    else if (this.count == that.count) 0
    else -1
  }
}