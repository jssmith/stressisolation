package stressisolation

object DatabaseVendor extends Enumeration {
  type DatabaseVendor = Val
  protected case class Val(databaseName: String) extends super.Val

  implicit def valueToDatabaseVendorVal(x: Value): Val = x.asInstanceOf[Val]

  val MySql, Postgresql, Oracle, DB2, SqlServer = Val(databaseName = "stressisolation")
  val SQLite = Val(databaseName = "main")
}
