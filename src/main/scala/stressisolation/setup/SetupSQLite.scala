package stressisolation.setup

import java.sql.Connection

import stressisolation.DatabaseVendor
import stressisolation.DatabaseVendor.DatabaseVendor

class SetupSQLite(val autoCommit: Boolean) extends DatabaseSetup {

  override def getDatabaseVendor: DatabaseVendor = DatabaseVendor.SQLite

  override def createTables(conn: Connection): Unit = {
    def createAccountTable(tableName: String): Unit = {
      val sql =s"""
                 |CREATE TABLE $databaseName.$tableName (
                 |  id INT NOT NULL,
                 |  balance INT,
                 |  PRIMARY KEY(id)
                 |)
                 |""".stripMargin
      execSql(conn, sql)
    }
    createAccountTable("checking")
    createAccountTable("savings")
  }
}
