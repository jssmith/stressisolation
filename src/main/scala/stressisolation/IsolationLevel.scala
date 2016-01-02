package stressisolation

import java.sql.Connection._

object IsolationLevel extends Enumeration {

  type IsolationLevel = Value

  val REPEATABLE_READ, READ_COMMITTED, READ_UNCOMMITTED, SERIALIZABLE = Value

  def getJdbcIsolationLevel(isolationLevel: Value) = {
    isolationLevel match {
      case REPEATABLE_READ => TRANSACTION_REPEATABLE_READ
      case READ_COMMITTED => TRANSACTION_READ_COMMITTED
      case READ_UNCOMMITTED => TRANSACTION_READ_UNCOMMITTED
      case SERIALIZABLE => TRANSACTION_SERIALIZABLE
    }
  }

}
