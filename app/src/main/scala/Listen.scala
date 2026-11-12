import java.io.{BufferedReader, IOException, InputStreamReader}
import java.net.{DatagramPacket, DatagramSocket, InetAddress, Socket, SocketTimeoutException}
import java.nio.charset.StandardCharsets.ISO_8859_1
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.CountDownLatch

// Écouteur temporaire : se connecte aux deux canaux du simulateur, horodate chaque ligne
// et l'affiche telle quelle. Reconnexion automatique (le simulateur coupe parfois la connexion).
//
//   sbt app/run
//   sbt "app/run --protocol tcp"
//   sbt "app/run --arduino 192.168.1.20:9001 --phidget 192.168.1.20:9002"

object Feed {
  private val clock = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
  private val Num = "-?\\d+(?:\\.\\d+)?"
  private val ArduinoOk = ("^(LIGHT,#|TEMP,#|PM,ERR|PM,#,#|ACK,\\S+|BOOT .*|INFO .*)$").replace("#", Num).r

  private def show(source: String, line: String): Unit = {
    val flag =
      if (source == "ARDUINO" && ArduinoOk.findFirstIn(line).isEmpty) "??"
      else "  "
    println(s"${clock.format(LocalTime.now())}  ${source.padTo(8, ' ')} $flag $line")
  }

  private def connectTcp(source: String, host: String, port: Int): Unit = {
    val t = new Thread(() => {
      while (true) {
        try {
          val s = new Socket(host, port)
          System.err.println(s"[$source] TCP connecté à $host:$port")

          try {
            val in = new BufferedReader(new InputStreamReader(s.getInputStream, ISO_8859_1))

            Iterator.continually(in.readLine())
              .takeWhile(_ != null)
              .map(_.trim)
              .filter(_.nonEmpty)
              .foreach(show(source, _))

          } finally {
            s.close()
          }
          System.err.println(s"[$source] connexion fermée")
        } catch {
          case e: IOException => System.err.println(s"[$source] TCP indisponible (${e.getMessage})")
        }
        Thread.sleep(2000)
      }
    }, s"tcp-$source")

    t.setDaemon(true)
    t.start()
  }

  private def connectUdp(source: String, host: String, port: Int): Unit = {
    val t = new Thread(() => {
      var socket: DatagramSocket = null

      try {
        socket = new DatagramSocket()
        socket.setSoTimeout(2000)
        val address = InetAddress.getByName(host)

        System.err.println(s"[$source] UDP écoute via $host:$port")

        def register(): Unit = {
          val packet = new DatagramPacket(Array.emptyByteArray, 0, address, port)
          socket.send(packet)
        }

        register()

        while (true) {
          try {
            val buffer = new Array[Byte](65507)
            val packet = new DatagramPacket(buffer, buffer.length)

            socket.receive(packet)

            val message = new String(packet.getData, packet.getOffset, packet.getLength, ISO_8859_1)

            message.split("\\r?\\n")
              .map(_.trim)
              .filter(_.nonEmpty)
              .foreach(show(source, _))

          } catch {
            case _: SocketTimeoutException => register()
          }
        }

      } catch {
        case e: IOException => System.err.println(s"[$source] UDP erreur : ${e.getMessage}")
      } finally {
        if (socket != null) socket.close()
      }
    }, s"udp-$source")

    t.setDaemon(true)
    t.start()
  }

  def connect(source: String, host: String, port: Int, protocol: String): Unit = protocol.toLowerCase(Locale.ROOT) match {
    case "tcp" => connectTcp(source, host, port)
    case "udp" => connectUdp(source, host, port)
    case x => throw new IllegalArgumentException(s"Protocole inconnu : $x (udp|tcp)")
  }

  def hostPort(s: String): (String, Int) = {
    val i = s.lastIndexOf(':')
    require(i > 0, s"Adresse invalide : $s")
    (s.substring(0, i), s.substring(i + 1).toInt)
  }
}


@main def listen(args: String*): Unit = {
  val o = args.sliding(2, 2)
    .collect { case Seq(k, v) => k -> v }
    .toMap

  val protocol = o.getOrElse("--protocol", "udp")
    .toLowerCase(Locale.ROOT)
  require(protocol == "tcp" || protocol == "udp", s"Protocole inconnu : $protocol (udp|tcp)")

  val (ah, ap) = Feed.hostPort(o.getOrElse("--arduino", "localhost:9001"))
  val (ph, pp) = Feed.hostPort(o.getOrElse("--phidget", "localhost:9002"))

  println(s"Protocole : ${protocol.toUpperCase(Locale.ROOT)}")

  Feed.connect("ARDUINO", ah, ap, protocol)
  Feed.connect("PHIDGET", ph, pp, protocol)

  new CountDownLatch(1).await()
}