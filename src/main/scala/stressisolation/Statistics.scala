package stressisolation

import java.util.concurrent.ConcurrentLinkedQueue

import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

import scala.collection.immutable.HashSet

class Statistics {
  private val statsLogger = Logger(LoggerFactory.getLogger("stressisolation.stats"))
  private val postgresAnomalyLogger = Logger(LoggerFactory.getLogger("stressisolation.stats.pganomaly"))

  private var runConfiguration: RunConfiguration = _
  private var balances: Array[(Int,(Int,Int),(Int,Int))] = _
  private val retries = new ConcurrentLinkedQueue[QueryIdentifier]()
  private val failures = new ConcurrentLinkedQueue[QueryIdentifier]()
  private var startTime: Long = _
  private var endTime: Long = _

  def startTiming(): Unit = {
    startTime = System.currentTimeMillis()
  }

  def endTiming(): Unit = {
    endTime = System.currentTimeMillis()
  }

  def setRunConfiguration(runConfiguration: RunConfiguration): Unit = {
    this.runConfiguration = runConfiguration
  }

  def recordRetry(queryIdentifier: QueryIdentifier): Unit = {
    retries.add(queryIdentifier)
  }

  def recordFailure(queryIdentifier: QueryIdentifier): Unit = {
    failures.add(queryIdentifier)
  }

  def recordResults(x: Iterable[(Int,(Int,Int),(Int,Int))]): Unit = {
    balances = x.toArray
  }

  def printDescription(): Unit = {
    object ResultClassification extends Enumeration {
      type ResultClassification = Value
      val Expected, ReadAnomaly, UpdateAnomaly, OtherAnomaly = Value
      def classify(r: ((Int,Int),(Int,Int))): ResultClassification = {
        r match {
          case ((0,0),(-10,20))
             | ((0,20),(-10,20))
             | ((-10,20),(-10,20))
             | ((0,0),(-11,20))
             | ((-11,0),(-11,20))
             | ((-11,20),(-11,20)) =>
            Expected
          case ((0,20),(-11,20)) =>
            ReadAnomaly
          case (_,(-10,0))
             | (_,(-11,0))
             | (_,(0,20))
             | (_, (0,0)) =>
            UpdateAnomaly
          case _ =>
            OtherAnomaly
        }
      }
    }
    val elapsedTime = endTime - startTime
    printf(s"${elapsedTime*1000/runConfiguration.numTests} tests/sec")
    printf("\nOutcome Statistics\n")
    val outcomes = balances.groupBy(x =>
      ResultClassification.classify((x._2,x._3)))
    val outcomeCounts = (for (c <- ResultClassification.values) yield {
      val numOutcomes = outcomes.get(c) match {
        case Some(x) => x.size
        case None => 0
      }
      c -> numOutcomes
    }).toArray
    for ((outcome,ct) <- outcomeCounts) {
      println(s"$outcome: $ct")
    }
    val retryIds = HashSet() ++ retries.toArray(new Array[QueryIdentifier](retries.size())).map(x => x.userId)
    val readAnomalyIds = outcomes.get(ResultClassification.ReadAnomaly) match {
      case Some(x) => HashSet[Int]() ++ x.map(_._1)
      case None => HashSet[Int]()
    }
    val failureCt = failures.size()
    val retryCt = retries.size()
    val retriedReadAnomaliesCt = retryIds.intersect(readAnomalyIds).size
    printf(s"Failures: $failureCt\n")
    printf(s"Retries: $retryCt\n")
    printf(s"Retries also read anomalies $retriedReadAnomaliesCt\n")
    statsLogger.info(
      s"${runConfiguration.databaseVendor},${runConfiguration.numConnections},"+
      s"${runConfiguration.numTests},${runConfiguration.isolationLevel}," +
      s"${runConfiguration.autoCommit},$elapsedTime,${outcomeCounts.map(_._2).mkString(",")}," +
      s"$failureCt,$retryCt,$retriedReadAnomaliesCt")

    // Log anomalies with Postgres under serializable isolation in more detail
    if (runConfiguration.databaseVendor == DatabaseVendor.Postgresql &&
        runConfiguration.isolationLevel == IsolationLevel.SERIALIZABLE) {
      outcomes.get(ResultClassification.ReadAnomaly) match {
        case Some(x) =>
          for (a <- x) {
            postgresAnomalyLogger.info("anomaly: " + a.toString)
          }
        case None =>
      }
    }
  }

}
