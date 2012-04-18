//package utils
//
//import scala.collection.mutable.ListBuffer
//import scala.collection.mutable.ArrayBuffer
//import java.io.Reader
//
//object Primes {
//  val array = Array(2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41)
//
//  def get(start: Int, num: Int) = {
//    if (start + num > array.length) throw new Exception("needs a longer prime list")
//    array.drop(start).take(num)
//  }
//}
//
//class Term(val varVals: Array[Byte]) {
//
//  override def toString(): String = {
//    var result = "{"
//    for (v <- varVals) {
//      if (v == Term.DONT_CARE)
//        result += "X"
//      else result += v
//      result += " "
//    }
//    result + "}"
//
//  }
//
//  def numVars = varVals.length
//
//  def combine(term: Term): Option[Term] = {
//    var diffVarNum = -1 // The position where they differ
//
//    for (i <- 0 until varVals.length) {
//      if (this.varVals(i) != term.varVals(i)) {
//        if (diffVarNum == -1) {
//          diffVarNum = i;
//        } else {
//          // They're different in at least two places
//          return None;
//        }
//      }
//    }
//    if (diffVarNum == -1) {
//      // They're identical
//      return null;
//    }
//    var resultVars = varVals.clone();
//    resultVars(diffVarNum) = Term.DONT_CARE;
//    return Some(new Term(resultVars));
//  }
//
//  def implies(term: Term): Boolean = {
//    for (i <- 0 until varVals.length) {
//      if (this.varVals(i) != Term.DONT_CARE &&
//        this.varVals(i) != term.varVals(i)) {
//        return false;
//      }
//    }
//    return true;
//  }
//
//  def countValues(b: Byte) = varVals.count(_ == b)
//
//  override def equals(o: Any) = o match {
//    case t: Term =>
//      (this eq t) || java.util.Arrays.equals(this.varVals, t.varVals)
//    case None => false
//  }
//
//  override def hashCode() = {
//    val mul = varVals zip Primes.get(5, varVals.size)
//    (mul map { x => x._1 * x._2 } sum) * 27 / 17
//  }
//
//}
//
//object Term {
//
//  val DONT_CARE: Byte = 2
//  val TRUE: Byte = 1
//  val FALSE: Byte = 0
//
//  def read(reader: Reader): Option[Term] = {
//    var c: Int = '\0';
//    val t = ArrayBuffer[Byte]()
//    while (c != '\n' && c != -1) {
//      c = reader.read();
//      if (c == '0') {
//        t += 0;
//      } else if (c == '1') {
//        t += 1;
//      }
//    }
//    if (t.size > 0) {
//      Some(new Term(t.toArray))
//    } else {
//      None
//    }
//  }
//}
//
//class Formula(var termList: ListBuffer[Term]) {
//
//  def this(terms: List[Term]) = this(new ListBuffer[Term]() ++ terms)
//
//  //var termList = new ListBuffer[Term]() ++ terms
//  private var originalTermList: ListBuffer[Term] = null
//
//  override def toString(): String = {
//    var result = ""
//    result += termList.size + " terms, " + termList(0).varVals.size + " variables \n"
//    for (term <- termList) {
//      result += term + "\n"
//    }
//    result
//  }
//
//  def reducePrimeImplicantsToSubset() {
//    // create implies table
//    val numPrimeImplicants = termList.size;
//    val numOriginalTerms = originalTermList.size;
//    val table = Array.ofDim[Boolean](numPrimeImplicants, numOriginalTerms)
//    for (
//      impl <- 0 until numPrimeImplicants;
//      term <- 0 until numOriginalTerms
//    ) {
//      table(impl)(term) = termList(impl).implies(originalTermList(term));
//    }
//
//    // extract implicants heuristically until done
//    var newTermList = ListBuffer[Term]();
//    var done = false;
//    var impl = 0;
//    while (!done) {
//      impl = extractEssentialImplicant(table)
//      if (impl != -1) {
//        newTermList += termList(impl)
//      } else {
//        impl = extractLargestImplicant(table)
//        if (impl != -1) {
//          newTermList += termList(impl)
//        } else {
//          done = true
//        }
//      }
//    }
//
//    termList = newTermList;
//    originalTermList = null
//  }
//
//  private def extractEssentialImplicant(table: Array[Array[Boolean]]): Int = {
//    for (term <- 0 until table(0).length) {
//      var lastImplFound = -1
//
//      var impl = 0
//      var break = false
//      while (impl < table.length && !break) {
//        if (table(impl)(term)) {
//          if (lastImplFound == -1) {
//            lastImplFound = impl
//          } else {
//            // This term has multiple implications
//            lastImplFound = -1
//            break = true
//          }
//        }
//
//        impl += 1
//      }
//
//      if (lastImplFound != -1) {
//        extractImplicant(table, lastImplFound)
//        return lastImplFound
//      }
//    }
//    -1
//  }
//
//  private def extractImplicant(table: Array[Array[Boolean]], impl: Int) {
//    for (
//      term <- 0 until table(0).length if (table(impl)(term));
//      impl2 <- 0 to table.length
//    ) {
//      table(impl2)(term) = false
//    }
//
//  }
//
//  private def extractLargestImplicant(table: Array[Array[Boolean]]): Int = {
//    var maxNumTerms = 0;
//    var maxNumTermsImpl = -1;
//    for (impl <- 0 until table.length) {
//      var numTerms = 0;
//      for (term <- 0 until table(0).length) {
//        if (table(impl)(term)) {
//          numTerms += 1;
//        }
//      }
//      if (numTerms > maxNumTerms) {
//        maxNumTerms = numTerms;
//        maxNumTermsImpl = impl;
//      }
//    }
//    if (maxNumTermsImpl != -1) {
//      extractImplicant(table, maxNumTermsImpl);
//      return maxNumTermsImpl;
//    }
//    return -1;
//  }
//
//  def reduceToPrimeImplicants() {
//
//    originalTermList = new ListBuffer[Term]();
//    //create term table
//    // rearrange data into a two dimension table of lists.
//    // Each element in the table is a list!
//
//    var numVars = termList(0).numVars;
//    val table = Array.fill(numVars + 1, numVars + 1)(ListBuffer[Term]());
//    for (i <- 0 until termList.size) {
//      var dontCares = termList(i).countValues(Term.DONT_CARE)
//      var ones = termList(i).countValues(Term.TRUE)
//      table(dontCares)(ones) += termList(i)
//    }
//
//    // generate new terms with combine() while updating prime implicant list
//
//    for (dontKnows <- 0 to numVars - 1) {
//      for (ones <- 0 to numVars - 1) {
//        val left = table(dontKnows)(ones)
//        val right = table(dontKnows)(ones + 1)
//        var out = table(dontKnows + 1)(ones);
//        for (
//          leftIdx <- 0 until left.size;
//          rightIdx <- 0 until right.size
//        ) {
//          left(leftIdx) combine right(rightIdx) match {
//            case Some(combined) =>
//              if (!out.contains(combined)) {
//                out += combined
//                // next, update prime implicant list 
//                termList -= left(leftIdx)
//                termList -= right(rightIdx)
//                if (!termList.contains(combined)) {
//                  termList += combined
//                }
//              }
//            case None =>
//          }
//        }
//      }
//    }
//  }
//
//}
//
//object Formula {
//
//  def read(reader: Reader): Option[Formula] = {
//    var terms = new ListBuffer[Term]();
//
//    var continue = true
//    while (continue) {
//      Term.read(reader) match {
//        case Some(term) => terms += term
//        case None => continue = false
//      }
//    }
//
//    if (terms.size > 0)
//      Some(new Formula(terms))
//    else None
//  }
//}
//
//object QuinMcCluskey {
//  import java.io._
//
//  def main(args: Array[String]) {
//
//    val f = Formula.read(new BufferedReader(new FileReader(args(0)))).get
//    f.reduceToPrimeImplicants();
//    System.out.println(f);
//    f.reducePrimeImplicantsToSubset();
//    System.out.println(f);
//  }
//}