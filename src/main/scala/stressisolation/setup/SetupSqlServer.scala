package stressisolation.setup

import java.sql.Connection

import stressisolation.DatabaseVendor
import stressisolation.DatabaseVendor._

class SetupSqlServer(val autoCommit: Boolean) extends DatabaseSetup {

   override def getDatabaseVendor: DatabaseVendor = DatabaseVendor.MySql

   override def createTables(conn: Connection): Unit = {
     def createAccountTable(tableName: String): Unit = {
       execSql(conn, s"CREATE TABLE $databaseName.$tableName (id INT PRIMARY KEY, balance INT)")
     }
     execSql(conn, s"CREATE SCHEMA $databaseName")

     createAccountTable("checking")
     createAccountTable("savings")
   }
 }
