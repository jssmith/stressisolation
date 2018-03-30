package stressisolation.dbconnection

import java.sql.DriverManager
import java.util.Properties

import stressisolation.DatabaseVendor._

trait DatabaseConnectionSource {
  val databaseVendor: DatabaseVendor
  val autoCommit: Boolean

  var initialized = false
  var dbUrl, user, pass: String = _

  def requireInit(): Unit = {
    if (!initialized) {
      // Load properties
      val p = new Properties()
      val propertiesFileName = "stressisolation.properties"
      val in = getClass.getResourceAsStream(s"/$propertiesFileName")
      if (in == null) {
        throw new RuntimeException(s"unable to load properties from $propertiesFileName")
      }
      p.load(in)

      def loadProperty(propertyName: String) = {
        val propertyPrefix = databaseVendor.toString.toLowerCase
        val propertyKey = s"$propertyPrefix.$propertyName"
        val propertyVal = p.getProperty(propertyKey)
        if (propertyVal == null) {
          throw new RuntimeException(s"undefined property $propertyKey")
        }
        propertyVal
      }

      dbUrl = loadProperty("url")
      user = loadProperty("user")
      pass = loadProperty("pass")

      // Register driver classes
      databaseVendor match {
        case MySql =>
          println("registered mysql driver")
          DriverManager.registerDriver(new com.mysql.jdbc.Driver)
        case DB2 =>
          DriverManager.registerDriver(new com.ibm.db2.jcc.DB2Driver)
        case Oracle =>
          DriverManager.registerDriver(new oracle.jdbc.OracleDriver)
        case Postgresql =>
          DriverManager.registerDriver(new org.postgresql.Driver)
        case SqlServer =>
          DriverManager.registerDriver(new com.microsoft.sqlserver.jdbc.SQLServerDriver)
        case SQLite =>
          DriverManager.registerDriver(new org.sqlite.JDBC)
      }

      initialized = true
    }
  }

  def getConnection = {
    requireInit()
    println(s"connect to $dbUrl")
    val conn = DriverManager.getConnection(dbUrl, user, pass)
    conn.setAutoCommit(autoCommit)
    conn
  }
}
