package app

import java.io.{BufferedReader, IOException, InputStreamReader}
import java.net.Socket
import java.nio.charset.StandardCharsets.ISO_8859_1
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CountDownLatch

// Écouteur temporaire : se connecte aux deux canaux du simulateur, horodate chaque ligne
// et l'affiche telle quelle. Reconnexion automatique (le simulateur coupe parfois la connexion).
//
//   sbt app/run
//   sbt "app/run --arduino 192.168.1.20:9001 --phidget 192.168.1.20:9002"

object Feed {
  private val clock = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

  private val Num = "-?\\d+(?:\\.\\d+)?" // entier ou décimal
  private val ArduinoOk =
    ("^(LIGHT,#|TEMP,#|PM,ERR|PM,#,#|ACK,\\S+|BOOT .*|INFO .*)$").replace("#", Num).r

  private def show(source: String, line: String): Unit = {
    val flag = if (source == "ARDUINO" && ArduinoOk.findFirstIn(line).isEmpty) "??" else "  "
    println(s"${clock.format(LocalTime.now)}  ${source.padTo(8, ' ')} $flag $line")
  }

  def connect(source: String, host: String, port: Int): Unit = {
    val t = new Thread(() => {
      while (true) {
        try {
          val s = new Socket(host, port)
          System.err.println(s"[$source] connecté à $host:$port")
          val in = new BufferedReader(new InputStreamReader(s.getInputStream, ISO_8859_1))
          Iterator.continually(in.readLine()).takeWhile(_ != null).map(_.trim).filter(_.nonEmpty).foreach(show(source, _))
          System.err.println(s"[$source] connexion fermée")
        } catch {
          case e: IOException => System.err.println(s"[$source] indisponible (${e.getMessage})")
        }
        Thread.sleep(2000)
      }
    })
    t.start()
  }

  def hostPort(s: String): (String, Int) = {
    val i = s.lastIndexOf(':')
    (s.substring(0, i), s.substring(i + 1).toInt)
  }
}

@main def listen(args: String*): Unit = {
  val o = args.sliding(2, 2).collect { case Seq(k, v) => k -> v }.toMap
  val (ah, ap) = Feed.hostPort(o.getOrElse("--arduino", "localhost:9001"))
  val (ph, pp) = Feed.hostPort(o.getOrElse("--phidget", "localhost:9002"))
  Feed.connect("ARDUINO", ah, ap)
  Feed.connect("PHIDGET", ph, pp)
  new CountDownLatch(1).await()
}