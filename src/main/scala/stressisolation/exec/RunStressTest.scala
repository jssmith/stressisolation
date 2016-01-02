package stressisolation.exec

import java.util.concurrent.ArrayBlockingQueue

import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory
import stressisolation.DatabaseVendor._
import stressisolation.{DatabaseVendor, _}
import stressisolation.dbconnection.DatabaseConnectionSource
import stressisolation.setup.DatabaseSetup

import scala.collection.mutable.ArrayBuffer
import scala.util.Random

object RunStressTest extends App {
  val logger = Logger(LoggerFactory.getLogger(RunStressTest.getClass))
  if (args.length != 5) {
    System.err.println(
      """Usage RunStressTest (Oracle | MySql | DB2 | Postgres | SqlServer) numConnections numTests (READ_COMMITTED | SERIALIZABLE) autocommit
        |  Suggested values:
        |    numConnections = 30
        |    numTests = 1000
        |    autoCommit = false
      """.stripMargin)
    System.exit(1)
  }
  val config = new RunConfiguration(
    databaseVendor = DatabaseVendor.withName(args(0)),
    numConnections = args(1).toInt,
    numTests = args(2).toInt,
    isolationLevel = IsolationLevel.withName(args(3)),
    autoCommit = args(4).toBoolean
  )
  if (config.numTests > DatabaseSetup.numAccounts) {
    throw new RuntimeException(s"number of tests ${config.numTests} exceeds limit of ${DatabaseSetup.numAccounts}")
  }
  val cs = new DatabaseConnectionSource {
    override val autoCommit: Boolean = config.autoCommit
    override val databaseVendor: DatabaseVendor = config.databaseVendor
  }

  val stats = new Statistics()
  stats.setRunConfiguration(config)
  val s = DatabaseSetup(cs)
  val conn = cs.getConnection
  try {
    s.zeroBalances(conn)
    class Task
    class QuitTask extends Task
    case class WorkTask (userId: Int, query: Int) extends Task

    val q = new ArrayBlockingQueue[Task](2 * config.numConnections)
    val threads = new Array[Thread](config.numConnections)
    val allReadResults = new ArrayBuffer[ReadResult]()
    for (i <- threads.indices) {
      threads(i) = new Thread(new Runnable {
        override def run(): Unit = {
          try {
            val conn = cs.getConnection
            s.setIsolationLevel(conn, config.isolationLevel)
            try {
              val ta = new TestAnomaly(conn, s, stats)
              val readResults = new ArrayBuffer[ReadResult]
              try {
                var nextTask: Task = q.poll()
                var failureCt = 0
                while (nextTask.isInstanceOf[WorkTask]) {
                  try {
                    val work = nextTask.asInstanceOf[WorkTask]
                    work match {
                      case WorkTask(userId, 1) => ta.txn1(userId)
                      case WorkTask(userId, 2) => ta.txn2(userId)
                      case WorkTask(userId, 3) =>
                        readResults += ta.txn3(userId)
                    }
                    failureCt = 0
                    nextTask = q.poll()
                  } catch {
                    case e: Exception =>
                      failureCt = failureCt + 1
                      if (failureCt > 3) {
                        throw new RuntimeException("Failure count exceeded",e)
                      }
                  }
                }
              } finally {
                allReadResults.synchronized {
                  allReadResults ++= readResults
                }
              }
            } finally {
              conn.close()
            }
          } catch {
            case e:Throwable => e.printStackTrace()
          }
        }
      })
    }
    threads.foreach(_.start)
    val rnd = new Random(234456)
    stats.startTiming()
    for (i <- 1 to config.numTests) {
      for (task <- rnd.shuffle(List(1, 2, 3))) {
        q.put(new WorkTask(i, task))
      }
      if (i % 100 == 0) {
        logger.info(s"progress $i")
      }
    }
    for (i <- 1 to config.numConnections) {
      q.put(new QuitTask)
    }
    logger.info("waiting on shutdown")
    threads.foreach(_.join)
    stats.endTiming()
    logger.info("threads finished")

    // save reads and final state
    val ta = new TestAnomaly(conn, s, stats)
    stats.recordResults(for (r1 <- allReadResults) yield {
      val r2 = ta.txn3(r1.userId)
      (r1.userId, r1.balances, r2.balances)
    })
  } finally {
    conn.close()
  }
  stats.printDescription()
}
