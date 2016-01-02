package stressisolation.setup

import java.sql.Connection

import stressisolation.DatabaseVendor
import stressisolation.DatabaseVendor._

class SetupOracle(val autoCommit: Boolean) extends DatabaseSetup {

  override def getDatabaseVendor: DatabaseVendor = DatabaseVendor.Oracle

  override def createTables(conn: Connection): Unit = {
     def createAccountTable(tableName: String): Unit = {
       execSql(conn, s"CREATE TABLE $databaseName.$tableName (id NUMBER PRIMARY KEY, balance NUMBER)")
     }
     execSql(conn, s"CREATE USER $databaseName IDENTIFIED BY xxx")
     execSql(conn, s"GRANT UNLIMITED TABLESPACE TO $databaseName")
     createAccountTable("checking")
     createAccountTable("savings")
   }
 }
