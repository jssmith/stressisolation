package stressisolation.exec

import stressisolation.DatabaseVendor
import stressisolation.DatabaseVendor._
import stressisolation.dbconnection.DatabaseConnectionSource


object TestConnection extends App {
  if (args.length != 1) {
    System.err.println("Usage TestConnection (Oracle | MySql | DB2 | Postgres | SqlServer)")
    System.exit(1)
  }
  val dbv = DatabaseVendor.withName(args(0))
  val cs = new DatabaseConnectionSource {
    override val autoCommit: Boolean = true
    override val databaseVendor: DatabaseVendor = dbv
  }

  val conn = cs.getConnection
  val sql = dbv match {
    case MySql | Postgresql | SqlServer => "SELECT 1 x"
    case Oracle => "SELECT 1 x FROM DUAL"
    case DB2 => "SELECT COUNT(*) x FROM ORG"
  }
  try {
    val stmt = conn.createStatement()
    try {
      val rs = stmt.executeQuery(sql)
      try {
        while (rs.next()) {
          println(rs.getString("x"))
        }
      } finally {
        rs.close()
      }
    } finally {
      stmt.close()
    }
  } finally {
    conn.close()
  }
  println(s"test complete for $dbv")
}
