package stressisolation.setup

import java.sql.Connection

import stressisolation.DatabaseVendor
import stressisolation.DatabaseVendor.DatabaseVendor

class SetupDB2(val autoCommit: Boolean) extends DatabaseSetup {

  override def getDatabaseVendor: DatabaseVendor = DatabaseVendor.DB2

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
