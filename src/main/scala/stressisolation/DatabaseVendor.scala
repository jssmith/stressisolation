package stressisolation

object DatabaseVendor extends Enumeration {
  type DatabaseVendor = Value
  val MySql, Postgresql, Oracle, DB2, SqlServer = Value
}
