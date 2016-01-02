package stressisolation.exec

import stressisolation.setup._
import stressisolation.DatabaseVendor
import stressisolation.DatabaseVendor._
import stressisolation.dbconnection.DatabaseConnectionSource

object SetupStressTest extends App {
  if (args.length != 1) {
    System.err.println("Usage SetupStressTest (Oracle | MySql | DB2 | Postgres | SqlServer)")
    System.exit(1)
  }
  val databaseVendorConfig = DatabaseVendor.withName(args(0))
  val cs = new DatabaseConnectionSource {
    override val autoCommit: Boolean = false
    override val databaseVendor: DatabaseVendor = databaseVendorConfig
  }

  val s = DatabaseSetup(cs)
  val conn = cs.getConnection
  try {
    s.createTables(conn)
    s.populateTables(conn)
  } finally {
    conn.close()
  }
}
