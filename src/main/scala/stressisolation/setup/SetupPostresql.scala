package stressisolation.setup

import java.sql.Connection

import stressisolation.DatabaseVendor
import stressisolation.DatabaseVendor._

class SetupPostresql(val autoCommit: Boolean) extends DatabaseSetup {

  override def getDatabaseVendor: DatabaseVendor = DatabaseVendor.Postgresql

  override def createTables(conn: Connection): Unit = {
     def createAccountTable(tableName: String): Unit = {
       val sql =
         s"""
            |CREATE TABLE $databaseName.$tableName (
            |  id INTEGER,
            |  balance INTEGER,
              PRIMARY KEY (id))
         """.stripMargin
       execSql(conn, sql)
     }
     execSql(conn, s"DROP SCHEMA IF EXISTS $databaseName CASCADE")
     execSql(conn, s"CREATE SCHEMA $databaseName")
     Thread.sleep(1000)
     createAccountTable("checking")
     createAccountTable("savings")
   }
 }
