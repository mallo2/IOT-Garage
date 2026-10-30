/*

import com.phidget22.{PhidgetException, VoltageRatioInput}

import scala.io.StdIn

@main def rfidMain(): Unit =
  val rfid = new RFID()

  rfid.addTagListener(e => println(s"Tag détecté : ${e.getTag} (${e.getProtocol})"))
  rfid.addTagLostListener(e => println(s"Tag perdu : ${e.getTag}"))

  try
    rfid.open(5000)
    rfid.setAntennaEnabled(true)
    println("Lecteur détecté. Approche un tag, Entrée pour quitter.")
    scala.io.StdIn.readLine()
    ()
  catch
    case e: PhidgetException => println(s"Erreur Phidget : ${e.getMessage}")
  finally
    try rfid.close()
    catch case _: PhidgetException => ()


@main def balanceMain(): Unit =
  val loadCell = new VoltageRatioInput()

  def averageReading(samples: Int = 20): Double =
    var total = 0.0
    for _ <- 1 to samples do
      Thread.sleep(50)
      total += loadCell.getVoltageRatio()
    total / samples

  try
    // Connecte le canal 0 du PhidgetBridge
    loadCell.setChannel(0)
    loadCell.open(5000)

    loadCell.setBridgeEnabled(true)
    loadCell.setDataInterval(50)
    loadCell.setVoltageRatioChangeTrigger(0.0)

    println("PhidgetBridge connecté.")
    println()
    println("=== Calibration de la balance ===")
    println("Retire tout poids de la balance.")
    println("Appuie sur Entrée lorsque la balance est vide.")
    StdIn.readLine()

    println("Mesure du zéro...")
    val zero = averageReading()

    println(f"Valeur à vide : $zero%.8f")
    println()
    println("Place un poids de référence sur la balance.")
    print("Masse connue en grammes (ex. 500) : ")

    val referenceGrams = StdIn.readLine().trim.toDouble

    require(referenceGrams > 0, "La masse doit être positive.")

    println("Mesure du poids de référence...")
    val loaded = averageReading()

    val difference = loaded - zero

    if math.abs(difference) < 1e-9 then
      throw new IllegalStateException(
        "Signal trop faible : vérifie la cellule et son câblage."
      )

    val gramsPerRatio = referenceGrams / difference

    println(f"Référence mesurée : $loaded%.8f")
    println("Calibration terminée.")
    println()
    println("Retire le poids de référence.")
    println("La balance affiche maintenant les grammes.")
    println("Appuie sur Entrée pour quitter.")

    // Affiche le poids en continu
    val reader = new Thread(() =>
      try
        while !Thread.currentThread().isInterrupted do
          val ratio = loadCell.getVoltageRatio()
          val grams = (ratio - zero) * gramsPerRatio
          println(f"Poids : $grams%.2f g")
          Thread.sleep(250)
      catch
        case _: InterruptedException => ()
        case e: PhidgetException =>
          println(s"Erreur de lecture : ${e.getMessage}")
    )

    reader.setDaemon(true)
    reader.start()

    StdIn.readLine()
    reader.interrupt()

  catch
    case e: PhidgetException =>
      println(s"Erreur Phidget : ${e.getMessage}")
    case e: NumberFormatException =>
      println("Erreur : entre une masse numérique, par exemple 500.")
    case e: IllegalArgumentException =>
      println(s"Erreur de calibration : ${e.getMessage}")
    case e: IllegalStateException =>
      println(s"Erreur : ${e.getMessage}")

  finally
    try loadCell.close()
    catch
      case _: PhidgetException => ()
*/
package app

import java.io.{FileWriter, PrintWriter}
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneId}
import java.util.Locale
import scala.collection.mutable

// Accolades et parenthèses partout : compile même sans syntaxe par indentation.
//
// Ce programme est l'app "côté Pi", version minimale : il se connecte aux deux canaux
// (Arduino et Phidgets), pose l'horodatage à la réception (comme le ferait l'adaptateur réel),
// parse les lignes, vérifie le checksum Arduino et affiche les événements.
//
//   sbt app/run
//   sbt "app/run --throttle-ms 0 --record capture.log"
//   sbt "app/run --arduino 192.168.1.20:9001 --phidget 192.168.1.20:9002"

// ── Événements du domaine ────────────────────────────────────────────────────
sealed trait Event { def ts: Instant }
final case class Temperature(celsius: Double, ts: Instant) extends Event
final case class Light(lux: Int, ts: Instant) extends Event
final case class Particles(pm25: Int, pm10: Int, ts: Instant) extends Event
final case class SensorError(sensor: String, ts: Instant) extends Event
final case class ArduinoDebug(text: String, ts: Instant) extends Event
final case class Ack(cmd: String, ts: Instant) extends Event
final case class Humidity(percent: Double, ts: Instant) extends Event
final case class ScaleRatio(ratio: Double, ts: Instant) extends Event
final case class TagScanned(tag: String, proto: String, ts: Instant) extends Event
final case class TagLost(tag: String, ts: Instant) extends Event
final case class Attached(device: String, ts: Instant) extends Event
final case class Detached(device: String, ts: Instant) extends Event

// ── Checksum : même calcul que le firmware et le simulateur ──────────────────
object Nmea {
  def checksum(body: String): Int = body.foldLeft(0)((acc, c) => acc ^ c)
}

// ── Parser Arduino : "$CAPTEUR,val1,val2*CS" ─────────────────────────────────
object ArduinoParser {
  private val Framed = """^\$([^*]+)\*([0-9A-Fa-f]{2})$""".r

  def parse(line: String, ts: Instant): Either[String, Event] = {
    line match {
      case Framed(body, cs) =>
        if (Nmea.checksum(body) != Integer.parseInt(cs, 16)) {
          Left("checksum invalide")
        } else {
          body.split(",", -1).toList match {
            case "TEMP" :: v :: Nil =>
              v.toDoubleOption.map(x => Temperature(x, ts)).toRight("valeur TEMP invalide")
            case "LIGHT" :: v :: Nil =>
              v.toIntOption.map(x => Light(x, ts)).toRight("valeur LIGHT invalide")
            case "PM" :: "ERR" :: Nil =>
              Right(SensorError("PM", ts))
            case "PM" :: a :: b :: Nil =>
              (for { x <- a.toIntOption; y <- b.toIntOption } yield Particles(x, y, ts))
                .toRight("valeurs PM invalides")
            case "ACK" :: cmd :: Nil =>
              Right(Ack(cmd, ts))
            case _ =>
              Left(s"capteur inconnu : $body")
          }
        }
      case s if s.startsWith("BOOT") || s.startsWith("INFO") =>
        Right(ArduinoDebug(s, ts)) // lignes de debug sans '$' : acceptées, pas des mesures
      case _ =>
        Left("trame mal formée")
    }
  }
}

// ── Parser Phidgets : "<Classe> serial=.. port=.. ch=.. cle=valeur" ──────────
object PhidgetParser {
  private val Kv = """(\w+)=(\S+)""".r

  def parse(line: String, ts: Instant): Either[String, Event] = {
    val parts = line.split("\\s+", 2)
    if (parts.length < 2) {
      Left("ligne Phidget trop courte")
    } else {
      val cls = parts(0)
      val kv = Kv.findAllMatchIn(parts(1)).map(m => m.group(1) -> m.group(2)).toMap
      (cls, kv.get("event")) match {
        case (_, Some("attach")) => Right(Attached(cls, ts))
        case (_, Some("detach")) => Right(Detached(cls, ts))
        case ("RFID", Some("tag")) =>
          kv.get("tag").map(t => TagScanned(t, kv.getOrElse("proto", "?"), ts)).toRight("tag manquant")
        case ("RFID", Some("tagLost")) =>
          kv.get("tag").map(t => TagLost(t, ts)).toRight("tag manquant")
        case ("HumiditySensor", None) =>
          kv.get("humidity").flatMap(_.toDoubleOption).map(v => Humidity(v, ts)).toRight("humidity invalide")
        case ("VoltageRatioInput", None) =>
          kv.get("voltageRatio").flatMap(_.toDoubleOption).map(v => ScaleRatio(v, ts)).toRight("voltageRatio invalide")
        case _ =>
          Left("événement Phidget inconnu")
      }
    }
  }
}

// ── Source TCP avec reconnexion automatique ──────────────────────────────────
// En release, ce sera remplacé par un SerialSource (/dev/ttyACM0) et le vrai PhidgetSource :
// ils appellent le même handle(source, ligne).
final class TcpSource(name: String, host: String, port: Int, onLine: String => Unit) {
  @volatile private var running = true

  def start(): Unit = {
    val t = new Thread(() => {
      while (running) {
        try {
          val s = new Socket(host, port)
          System.err.println(s"[$name] connecté à $host:$port")
          // ISO-8859-1 : un octet = un caractère, les octets parasites restent visibles
          scala.io.Source.fromInputStream(s.getInputStream, "ISO-8859-1").getLines().foreach(onLine)
          System.err.println(s"[$name] connexion fermée")
        } catch {
          case e: java.io.IOException =>
            System.err.println(s"[$name] $host:$port indisponible (${e.getMessage}), nouvel essai dans 2 s")
        }
        Thread.sleep(2000)
      }
    })
    t.start()
  }

  def close(): Unit = { running = false }
}

// ── Écoute : parse, compte, affiche, enregistre ──────────────────────────────
final class Listener(throttleMs: Long, recordPath: Option[String]) {
  private val lock = new Object
  private val clock = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())
  private val lastShown = mutable.Map.empty[String, Long]
  private val counts = mutable.Map.empty[String, Long]
  private val recorder: Option[PrintWriter] =
    recordPath.map(p => new PrintWriter(new FileWriter(p, StandardCharsets.UTF_8, true), true))

  private def fmt(f: String, x: Double): String = f.formatLocal(Locale.ROOT, x)

  private def isContinuous(e: Event): Boolean = e match {
    case _: Temperature | _: Light | _: Particles | _: Humidity | _: ScaleRatio => true
    case _ => false
  }

  private def describe(e: Event): String = e match {
    case Temperature(c, _)     => fmt("Temperature  %.2f °C", c)
    case Light(l, _)           => s"Light        $l lux"
    case Particles(a, b, _)    => s"Particles    PM2.5=$a  PM10=$b µg/m³"
    case SensorError(s, _)     => s"SensorError  $s en erreur"
    case ArduinoDebug(t, _)    => s"Debug        $t"
    case Ack(c, _)             => s"Ack          $c"
    case Humidity(h, _)        => fmt("Humidity     %.2f %%", h)
    case ScaleRatio(r, _)      => fmt("ScaleRatio   %.9f V/V (brut, à convertir en grammes)", r)
    case TagScanned(t, p, _)   => s"TagScanned   $t ($p)"
    case TagLost(t, _)         => s"TagLost      $t"
    case Attached(d, _)        => s"Attached     $d"
    case Detached(d, _)        => s"Detached     $d"
  }

  private def count(key: String): Unit = {
    counts(key) = counts.getOrElse(key, 0L) + 1
  }

  /** Point d'entrée commun à toutes les sources (TCP en dev, série / Phidget22 en release). */
  def handle(source: String, raw: String): Unit = {
    val ts = Instant.now // l'adaptateur pose l'horodatage : l'Arduino n'en a pas
    val line = raw.trim
    if (line.nonEmpty) {
      val result =
        if (source == "ARDUINO") ArduinoParser.parse(line, ts) else PhidgetParser.parse(line, ts)
      lock.synchronized {
        recorder.foreach(w => w.println(s"$ts $source $line")) // même format que sim.log
        val label = source.padTo(8, ' ')
        result match {
          case Right(e) =>
            count(e.getClass.getSimpleName)
            val key = e.getClass.getSimpleName
            val now = System.currentTimeMillis
            val show =
              !isContinuous(e) || throttleMs <= 0 || now - lastShown.getOrElse(key, 0L) >= throttleMs
            if (show) {
              lastShown(key) = now
              println(s"${clock.format(ts)}  $label ${describe(e)}")
            }
          case Left(err) =>
            count("Rejetees")
            println(s"${clock.format(ts)}  $label REJETÉE      $err : $line")
        }
      }
    }
  }

  /** Résumé toutes les 10 s sur stderr. */
  def startStats(): Unit = {
    val t = new Thread(() => {
      while (true) {
        Thread.sleep(10000)
        lock.synchronized {
          val summary = counts.toSeq.sortBy(_._1).map { case (k, v) => s"$k=$v" }.mkString("  ")
          System.err.println(s"[STATS] $summary")
        }
      }
    })
    t.setDaemon(true)
    t.start()
  }
}

// ── Main ─────────────────────────────────────────────────────────────────────
@main def listen(args: String*): Unit = {
  val opts = args.sliding(2, 2).collect { case Seq(k, v) => k -> v }.toMap

  def hostPort(s: String, defaultPort: Int): (String, Int) = {
    val i = s.lastIndexOf(':')
    if (i < 0) (s, defaultPort) else (s.substring(0, i), s.substring(i + 1).toInt)
  }

  val (arduinoHost, arduinoPort) = hostPort(opts.getOrElse("--arduino", "localhost:9001"), 9001)
  val (phidgetHost, phidgetPort) = hostPort(opts.getOrElse("--phidget", "localhost:9002"), 9002)
  val throttleMs = opts.getOrElse("--throttle-ms", "1000").toLong

  val listener = new Listener(throttleMs, opts.get("--record"))
  listener.startStats()

  new TcpSource("ARDUINO", arduinoHost, arduinoPort, l => listener.handle("ARDUINO", l)).start()
  new TcpSource("PHIDGET", phidgetHost, phidgetPort, l => listener.handle("PHIDGET", l)).start()

  // Bloque le thread principal : sans ça, `sbt app/run` se termine dès que main() rend la main
  // et tue les threads d'écoute. Ctrl+C pour arrêter.
  new java.util.concurrent.CountDownLatch(1).await()
}