package stressisolation

import stressisolation.DatabaseVendor._
import stressisolation.IsolationLevel._

case class RunConfiguration (
  databaseVendor: DatabaseVendor,
  numConnections: Int,
  numTests: Int,
  isolationLevel: IsolationLevel,
  autoCommit: Boolean
)
