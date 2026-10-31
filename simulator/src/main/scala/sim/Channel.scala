package sim

import java.io.{BufferedReader, IOException, InputStreamReader}
import java.net.{ServerSocket, Socket}
import java.nio.charset.StandardCharsets.ISO_8859_1
import java.util.concurrent.CopyOnWriteArrayList

trait Channel {
  def hasClients: Boolean
  def write(bytes: Array[Byte]): Unit
  def dropClients(): Unit
}

final class TcpChannel(name: String, port: Int, onConnect: () => Unit, onLine: String => Unit) extends Channel {

  private val clients = new CopyOnWriteArrayList[Socket]()

  def hasClients: Boolean = !clients.isEmpty

  def write(bytes: Array[Byte]): Unit =
    clients.forEach { s =>
      try {
        val o = s.getOutputStream
        o.write(bytes)
        o.flush()
      } catch {
        case _: IOException => clients.remove(s); ()
      }
    }

  /** Câble débranché : l'app voit la connexion tomber. */
  def dropClients(): Unit = {
    clients.forEach(s => closeQuietly(s))
    clients.clear()
  }

  def start(): Unit = {
    val acceptor = new Thread(() => {
      val server = new ServerSocket(port)
      System.err.println(s"[SIM] $name : en écoute sur le port $port")
      while (true) {
        try accept(server.accept())
        catch { case e: IOException => System.err.println(s"[SIM] $name : accept() a échoué : $e") }
      }
    }, s"accept-$name")
    acceptor.setDaemon(true)
    acceptor.start()
  }

  private def accept(s: Socket): Unit = {
    s.setTcpNoDelay(true)
    clients.add(s)
    System.err.println(s"[SIM] $name : client connecté (${s.getRemoteSocketAddress})")
    onConnect()

    val reader = new Thread(() => {
      try {
        val in = new BufferedReader(new InputStreamReader(s.getInputStream, ISO_8859_1))
        Iterator.continually(in.readLine()).takeWhile(_ != null).foreach(onLine)
      } catch { case _: IOException => () }
      finally { clients.remove(s); () }
    }, s"read-$name")
    reader.setDaemon(true)
    reader.start()
  }

  private def closeQuietly(s: Socket): Unit =
    try s.close()
    catch { case _: IOException => () }
}
