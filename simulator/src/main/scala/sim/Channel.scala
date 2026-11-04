package sim

trait Channel {
  def hasClients: Boolean
  def write(bytes: Array[Byte]): Unit
  def start(): Unit
  def dropClients(): Unit
}

object Channel {
  def create(name: String, port: Int, protocol: String, onConnect: () => Unit, onLine: String => Unit): Channel = protocol match {
    case "tcp" => new TcpChannel(name, port, onConnect, onLine)
    case "udp" => new UdpChannel(name, port, onConnect, onLine)
    case _ => throw new IllegalArgumentException(s"unknown channel protocol '$protocol'")
  }
}