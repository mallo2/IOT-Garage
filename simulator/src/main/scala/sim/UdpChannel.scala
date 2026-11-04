package sim

import java.io.IOException
import java.net.{DatagramPacket, DatagramSocket, InetSocketAddress, SocketException}
import java.nio.charset.StandardCharsets.ISO_8859_1
import java.util.concurrent.CopyOnWriteArrayList

final class UdpChannel(name: String, port: Int, onConnect: () => Unit, onLine: String => Unit) extends Channel {

  private val clients = new CopyOnWriteArrayList[InetSocketAddress]()

  @volatile private var socket: DatagramSocket = _

  def hasClients: Boolean = !clients.isEmpty

  def write(bytes: Array[Byte]): Unit = {
    val s = socket
    if (s != null && !s.isClosed) {
      clients.forEach { address =>
        try {
          val packet = new DatagramPacket(bytes, bytes.length, address)
          s.send(packet)
        } catch {
          case _: IOException => ()
        }
      }
    }
  }

  def dropClients(): Unit = {
    clients.clear()
  }

  def start(): Unit = {
    val receiver = new Thread(() => {
      try {
        socket = new DatagramSocket(port)
        System.err.println(s"[SIM] $name : UDP en écoute sur le port $port")

        while (!socket.isClosed) {
          try {
            val buffer = new Array[Byte](65507)
            val packet = new DatagramPacket(buffer, buffer.length)
            socket.receive(packet)
            val address = new InetSocketAddress(packet.getAddress, packet.getPort)

            if (!clients.contains(address)) {
              clients.add(address)
              System.err.println(s"[SIM] $name : client UDP connecté ($address)")
              onConnect()
            }

            val line = new String(packet.getData, packet.getOffset, packet.getLength, ISO_8859_1)
            onLine(line)
          } catch {
            case _: SocketException if socket.isClosed => ()
            case e: IOException => System.err.println(s"[SIM] $name : receive() a échoué : $e")
          }
        }

      } catch {
        case e: IOException => System.err.println(s"[SIM] $name : impossible de démarrer UDP : $e")
      }
    }, s"receive-$name")

    receiver.setDaemon(true)
    receiver.start()
  }

  def close(): Unit = {
    if (socket != null) {
      socket.close()
    }
    clients.clear()
  }
}