package stressisolation.setup

import java.sql.Connection

import stressisolation.DatabaseVendor
import stressisolation.DatabaseVendor._

class SetupMySql(val autoCommit: Boolean) extends DatabaseSetup {

  override def getDatabaseVendor: DatabaseVendor = DatabaseVendor.MySql

  override def createTables(conn: Connection): Unit = {
    def createAccountTable(tableName: String): Unit = {
      execSql(conn, s"CREATE TABLE $databaseName.$tableName (id INT PRIMARY KEY, balance INT)")
    }
    execSql(conn, s"DROP DATABASE IF EXISTS $databaseName")
    execSql(conn, s"CREATE DATABASE $databaseName")
    createAccountTable("checking")
    createAccountTable("savings")
  }
}
