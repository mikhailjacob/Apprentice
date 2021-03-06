package csv

import java.io._

object CSVProcessor {

  def main(args: Array[String]) {
    val text = scala.io.Source.fromFile("./movies/movie-original-data.csv").mkString
    
    val writer = new PrintWriter(new BufferedOutputStream(new FileOutputStream("movie-stories.txt")))
    
    val lines = text.split("\n")
    lines foreach {
      l =>
        val lineText = l.trim
        val events = lineText.split(",")
        if (events.length > 0) {
          var i = 0
          while (i < events.length) {
            
            var event = events(i)
            
            if (event.startsWith("\"")) {
              while (!events(i).endsWith("\"")) {
                i += 1
                event = event + "," + events(i)
              }
              event = event.substring(1, event.length - 1)
            }
            
            writer.println(event)
            i += 1
          }
          /*
          events foreach {
            e =>
              
          }
          println(events.mkString("", "\n", "\n###"))
          */
          writer.println("###")
        }
    }
    
    writer.close()
  }
}