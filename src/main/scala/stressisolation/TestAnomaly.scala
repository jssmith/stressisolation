package stressisolation

import java.sql.{Connection, SQLException}

import stressisolation.setup.DatabaseSetup

import scala.util.Random

class TestAnomaly(conn: Connection, databaseSetup: DatabaseSetup, stats: Statistics) {
  private val databaseVendor = databaseSetup.getDatabaseVendor
  private val autoCommit = databaseSetup.autoCommit
  private val databaseName = databaseVendor.databaseName

  val numRetries = 30

  private def retry[T](f: () => T, userId: Int, queryId: Int): T = {
    var n = numRetries
    while (n > 0) {
      try {
        return f()
      } catch {
        case e: SQLException =>
          n = n - 1
          if (n == 0) {
            // Note: this kills the thread
            stats.recordFailure(new QueryIdentifier(userId, queryId))
            throw e
          } else {
            stats.recordRetry(new QueryIdentifier(userId, queryId))
            if (!autoCommit) {
              conn.rollback()
            }
            //          println(desc)
            Thread.sleep(Random.nextInt(4 + numRetries - n) + 1)
          }
      }
    }
    null.asInstanceOf[T]
  }

  def txn1(userId: Int): Unit = {
    val sql =
      s"""
        |UPDATE $databaseName.savings SET balance = balance + 20
        |WHERE savings.id = ?
      """.stripMargin
    val stmt = conn.prepareStatement(sql)
    try {
      stmt.setInt(1, userId)
      retry(() => {
        stmt.execute()
        if (!autoCommit) {
          conn.commit()
        }
      }, userId = userId, queryId = 1)
    } finally {
      stmt.close()
    }
  }

  def txn2(userId: Int): Unit = {
    val sql = databaseVendor match {
      case DatabaseVendor.Postgresql => {
        s"""
          |UPDATE $databaseName.checking AS c
          |SET balance =
          |  CASE WHEN s.balance + c.balance >= 10 THEN
          |    c.balance - 10
          |  ELSE
          |    c.balance - 11
          |  END
          |FROM $databaseName.savings AS s
          |WHERE c.id = s.id AND c.id = ?
        """.stripMargin
      }
      case DatabaseVendor.MySql => {
        """
          |UPDATE stressisolation.checking c
          |JOIN stressisolation.savings s ON c.id = s.id
          |SET c.balance =
          |  CASE WHEN s.balance + c.balance >= 10 THEN
          |    c.balance - 10
          |  ELSE
          |    c.balance - 11
          |  END
          |WHERE c.id = ?
        """.stripMargin
      }
      case DatabaseVendor.Oracle => {
        s"""
          |UPDATE (SELECT c.id id, c.balance cb, s.balance sb
          |FROM $databaseName.checking c JOIN
          |  $databaseName.savings s ON c.id=s.id)
          |SET cb = CASE WHEN cb + sb >= 10 THEN
          |    cb - 10
          |  ELSE
          |    cb - 11
          |  END
          |WHERE id = ?
        """.stripMargin
      }
      case DatabaseVendor.DB2 => {
        s"""
          |UPDATE $databaseName.checking
          |  SET balance = (
          |    SELECT CASE WHEN c.balance + s.balance >= 10
          |      THEN c.balance - 10 ELSE c.balance - 11 END
          |     FROM $databaseName.checking c JOIN $databaseName.savings s
          |       ON c.id = s.id where c.id = ?
          |  )
          |WHERE id = ?
        """.stripMargin
      }
      case DatabaseVendor.SQLite => {
        """
          |UPDATE checking
          |  SET balance = (
          |    SELECT CASE WHEN checking.balance + savings.balance >= 10
          |      THEN checking.balance - 10 ELSE checking.balance - 11 END
          |     FROM checking JOIN savings
          |       ON checking.id = savings.id where checking.id = ?
          |  )
          |WHERE id = ?
        """.stripMargin
      }
    }
    val stmt = conn.prepareStatement(sql)
    try {
      stmt.setInt(1, userId)
      if (DatabaseVendor.DB2 == databaseVendor || DatabaseVendor.SQLite == databaseVendor) {
        stmt.setInt(2, userId)
      }
      retry(() => {
        stmt.execute()
        if (!autoCommit) {
          conn.commit()
        }
      }, userId = userId, queryId = 2)
    } finally {
      stmt.close()
    }
  }

  def txn3(userId: Int): ReadResult = {
    val sql =
      s"""
        |SELECT c.balance cb, s.balance sb
        |FROM $databaseName.checking c
        |JOIN $databaseName.savings s
        |ON c.id = s.id
        |WHERE s.id = ?
      """.stripMargin
    val stmt = conn.prepareStatement(sql)
    try {
      stmt.setInt(1, userId)
      retry(() => {
        stmt.execute()
        val rs = stmt.getResultSet
        if (!rs.next()) {
          throw new RuntimeException("expected a result")
        }
        val checkingBalance = rs.getInt(1)
        val savingsBalance = rs.getInt(2)
        if (!autoCommit) {
          conn.commit()
        }
        new ReadResult(userId, checkingBalance, savingsBalance)
      }, userId = userId, queryId = 3)
    } finally {
      stmt.close()
    }
  }

}
