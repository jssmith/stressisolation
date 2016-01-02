package stressisolation.setup

import java.sql.Connection

import stressisolation.DatabaseVendor.{DatabaseVendor, _}
import stressisolation.IsolationLevel
import stressisolation.dbconnection.DatabaseConnectionSource

import scala.util.Random

abstract class DatabaseSetup {
  val databaseName = "stressisolation"

  def getDatabaseVendor: DatabaseVendor

  def createTables(conn: Connection): Unit
  val autoCommit: Boolean

  def setIsolationLevel(conn: Connection, isolationLevel: IsolationLevel.IsolationLevel) = {
    conn.setTransactionIsolation(IsolationLevel.getJdbcIsolationLevel(isolationLevel))
  }

  def populateTables(conn: Connection): Unit = {
    val rnd = new Random(234234)
    val users = rnd.shuffle((1 to DatabaseSetup.numAccounts).toList)
    def initAll(tableName: String, users: Iterable[Int]): Unit = {
      val x = conn.prepareCall(s"INSERT INTO $databaseName.$tableName (id, balance) VALUES (?,0)")
      var ct = 0
      for (userId <- users) {
        x.setInt(1, userId)
        x.execute()
        if (ct % 10000 == 0) {
          println(ct)
          conn.commit()
        }
        ct += 1
      }
      conn.commit()
    }
    initAll("checking", users)
    initAll("savings", users)
  }

  def zeroBalances(conn: Connection): Unit = {
    def zeroTable(tableName: String): Unit = {
      execSql(conn, s"UPDATE $databaseName.$tableName SET balance = 0 WHERE balance <> 0")
    }
    zeroTable("checking")
    if (!autoCommit) {
      conn.commit()
    }
    zeroTable("savings")
    if (!autoCommit) {
      conn.commit()
    }
  }

  protected def execSql(conn: Connection, sql: String) = {
    val stmt = conn.prepareStatement(sql)
    try {
      stmt.execute()
    } finally {
      stmt.close()
    }
  }

}

object DatabaseSetup {
  val numAccounts = 100000
  def apply(cs: DatabaseConnectionSource) = {
    cs.databaseVendor match {
      case MySql => new SetupMySql(cs.autoCommit)
      case Oracle => new SetupOracle(cs.autoCommit)
      case Postgresql => new SetupPostresql(cs.autoCommit)
      case DB2 => new SetupDB2(cs.autoCommit)
      case SqlServer => new SetupSqlServer(cs.autoCommit)
    }
  }
}