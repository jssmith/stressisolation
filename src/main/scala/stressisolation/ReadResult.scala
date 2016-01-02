package stressisolation

case class ReadResult(userId: Int, checkingBalance: Int, savingsBalance: Int) {
  def balances = (checkingBalance, savingsBalance)
}
