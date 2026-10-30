package sim

import java.net.{ServerSocket, Socket}
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Locale
import java.util.concurrent.{CopyOnWriteArrayList, Executors, TimeUnit}
import scala.collection.mutable.ArrayDeque
import scala.util.Random

object Fmt {
  def f(fmt: String, x: Double): String = fmt.formatLocal(Locale.ROOT, x)
}

final class Channel(name: String, port: Int, onConnect: () => Unit, onLine: String => Unit) {
  private val clients = new CopyOnWriteArrayList[Socket]()

  def hasClients: Boolean = !clients.isEmpty

  def write(bytes: Array[Byte]): Unit = {
    clients.forEach { s =>
      try {
        val o = s.getOutputStream
        o.write(bytes)
        o.flush()
      } catch {
        case _: java.io.IOException =>
          clients.remove(s)
          ()
      }
    }
  }

  /** Câble débranché : l'app voit la connexion tomber. */
  def dropClients(): Unit = {
    clients.forEach { s =>
      try {
        s.close()
      } catch {
        case _: java.io.IOException => ()
      }
    }
    clients.clear()
  }

  def start(): Unit = {
    val acceptor = new Thread(() => {
      val server = new ServerSocket(port)
      System.err.println(s"[SIM] $name : en écoute sur le port $port")
      while (true) {
        val s = server.accept()
        s.setTcpNoDelay(true)
        clients.add(s)
        System.err.println(s"[SIM] $name : client connecté (${s.getRemoteSocketAddress})")
        onConnect()
        val reader = new Thread(() => {
          try {
            scala.io.Source.fromInputStream(s.getInputStream, "ISO-8859-1").getLines().foreach(onLine)
          } catch {
            case _: java.io.IOException => ()
          } finally {
            clients.remove(s)
          }
        })
        reader.setDaemon(true)
        reader.start()
      }
    })
    acceptor.setDaemon(true)
    acceptor.start()
  }
}

final class Simulation(scenario: String, arduinoPort: Int, phidgetPort: Int, baud: Int) {

  private val HumId = "serial=654321 port=0 ch=0"
  private val ScaleId = "serial=654321 port=1 ch=0"
  private val RfidId = "serial=432108 port=0"
  private val KnownTags = Vector("0100a1b2c3", "0100d4e5f6", "0200112233", "0100ffee01")
  private val UnknownTag = "0100deadbe"
  private val ScaleGain = 2.0e-6

  private val rnd = new Random()
  private val sched = Executors.newSingleThreadScheduledExecutor()
  private val t0 = System.currentTimeMillis
  private val chaos = if (scenario == "chaos") 5 else 1

  private val arduinoCh =
    new Channel("arduino", arduinoPort, () => later(0)(onArduinoConnect()), l => later(0)(onCommand(l)))
  private val phidgetCh =
    new Channel("phidget", phidgetPort, () => later(0)(onPhidgetConnect()), _ => ())

  private var temp = 22.0
  private var hum = 45.0
  private var lux = 300.0
  private var pm25 = 8.0
  private var targetG = 0.0
  private var shownG = 0.0
  private var vel = 0.0
  private var offset = 3.1e-5

  private final class Periodic(val period: Long, val body: () => String) {
    var nextAt = 0L
  }

  private val sensors: Vector[Periodic] = Vector(
    new Periodic(500, () => "LIGHT," + lux.toInt),
    new Periodic(2000, () => Fmt.f("TEMP,%.2f", temp)),
    new Periodic(
      5000,
      () => if (rnd.nextInt(50) == 0) "PM,ERR" else s"PM,${pm25.toInt},${(pm25 * 1.4).toInt}"
    )
  )
  private var downUntil = 0L
  private var booting = false
  private val uartBuf = new ArrayDeque[Byte]()
  private var credit = 0.0

  // état Phidgets
  @volatile private var phidgetsUp = true
  private var lastHum = Double.NaN
  private var lastRatio = Double.NaN
  private var tagOnReader: Option[String] = None

  private def attachPayloads: Vector[String] = Vector(
    s"HumiditySensor $HumId event=attach",
    s"VoltageRatioInput $ScaleId event=attach",
    s"RFID $RfidId event=attach"
  )

  private def task(body: => Unit): Runnable = new Runnable {
    def run(): Unit = {
      try {
        body
      } catch {
        case e: Exception => System.err.println(s"[SIM] erreur dans une tâche : $e")
      }
    }
  }

  private def every(ms: Long)(body: => Unit): Unit = {
    sched.scheduleAtFixedRate(task(body), 0, ms, TimeUnit.MILLISECONDS)
    ()
  }

  private def later(ms: Long)(body: => Unit): Unit = {
    sched.schedule(task(body), ms, TimeUnit.MILLISECONDS)
    ()
  }

  def start(): Unit = {
    arduinoCh.start()
    phidgetCh.start()
    every(10)(uartTick())
    every(10)(arduinoTick())
    every(1000)(physics())
    every(250)(humidityTick())
    every(200)(scaleTick())
    every(1000)(rfidTick())
  }

  // ── UART simulé : les octets sortent au rythme du baud rate (10 bits par octet) ──
  private def uartSend(payload: String): Unit = {
    println(s"${Instant.now} ARDUINO $payload")
    uartBuf ++= (payload + "\r\n").getBytes(StandardCharsets.ISO_8859_1)
  }

  private def uartTick(): Unit = {
    credit = math.min(credit + baud / 1000.0, baud / 500.0) // octets autorisés pendant ces 10 ms
    val n = math.min(credit.toInt, uartBuf.size)
    if (n > 0) {
      credit -= n
      val chunk = Array.fill(n)(uartBuf.removeHead())
      if (arduinoCh.hasClients) arduinoCh.write(chunk) // sans lecteur, les octets sont perdus
    }
  }

  // ── Firmware Arduino : un loop() qui lit chaque capteur à sa cadence ──────
  private def arduinoTick(): Unit = {
    val now = System.currentTimeMillis
    if (now >= downUntil) {
      if (booting) {
        booting = false
        uartSend("BOOT v1.2")
        uartSend("INFO sensors ready")
        sensors.foreach(s => s.nextAt = now + 500)
      }
      sensors.foreach { s =>
        if (now >= s.nextAt) {
          s.nextAt = now + s.period + rnd.nextInt(5) // gigue de loop()
          emit(s.body())
        }
      }
      if (rnd.nextInt(30000 / chaos) == 0) resetArduino("watchdog / brown-out")
    }
  }

  private def emit(body: String): Unit = {
    val line = body
    val r = rnd.nextInt(1000)

    val out =
      if (r < 2 * chaos) {
        val i = 1 + rnd.nextInt(line.length - 1)
        line.substring(0, i) +
          (line(i) ^ (1 << rnd.nextInt(7))).toChar +
          line.substring(i + 1)
      } else if (r < 3 * chaos) {
        "\u00ff" + line
      } else {
        line
      }

    uartSend(out)
  }

  private def resetArduino(why: String): Unit = {
    System.err.println(s"[SIM] Arduino reset ($why)")
    uartBuf.clear() // une ligne à moitié envoyée est tronquée
    booting = true
    downUntil = System.currentTimeMillis + 1500 + rnd.nextInt(1500) // bootloader : silence
  }

  private def onArduinoConnect(): Unit = {
    resetArduino("ouverture du port série (DTR)") // comportement d'un Uno / Nano
  }

  private def onCommand(raw: String): Unit = {
    val cmd = raw.trim
    System.err.println(s"[SIM] <- commande Arduino : $cmd")
    if (cmd.nonEmpty && System.currentTimeMillis >= downUntil) {
      emit("ACK," + cmd.toUpperCase(Locale.ROOT).replace(' ', '_'))
    }
  }

  // ── Monde physique ────────────────────────────────────────────────────────
  private def physics(): Unit = {
    val t = (System.currentTimeMillis - t0) / 1000.0
    val targetTemp = if (scenario == "canicule") 38.0 else 22.0 + 2 * math.sin(t / 300)
    temp += (targetTemp - temp) * 0.01 + rnd.nextGaussian() * 0.04
    hum = (hum + (75 - 1.4 * temp - hum) * 0.02 + rnd.nextGaussian() * 0.15).max(5).min(95)
    lux = (50 + 450 * (0.5 + 0.5 * math.sin(t / 120)) + rnd.nextGaussian() * 5).max(0)
    pm25 = (8 + (pm25 - 8) * 0.92 + rnd.nextGaussian() * 0.3).max(0)
    if (rnd.nextInt(60) == 0) pm25 += 40 + rnd.nextInt(60) // pic de pollution
    offset += rnd.nextGaussian() * 2e-9 // dérive de la cellule de charge

    // un objet est posé / retiré de la balance
    if (targetG == 0 && rnd.nextInt(25) == 0) {
      targetG = Vector(80.0, 250.0, 500.0, 1200.0)(rnd.nextInt(4))
    } else if (targetG > 0 && rnd.nextInt(12) == 0) {
      targetG = 0
    }

    // câble USB de l'Arduino arraché (scénario chaos uniquement) : l'app voit la connexion tomber
    if (scenario == "chaos" && rnd.nextInt(90) == 0) {
      System.err.println("[SIM] câble Arduino débranché")
      arduinoCh.dropClients()
    }

    // le hub USB des Phidgets se débranche de temps en temps
    if (phidgetsUp && rnd.nextInt(120 / chaos) == 0) {
      phidgetsUp = false
      tagOnReader = None
      lastHum = Double.NaN
      lastRatio = Double.NaN
      Vector(
        s"HumiditySensor $HumId event=detach",
        s"VoltageRatioInput $ScaleId event=detach",
        s"RFID $RfidId event=detach"
      ).foreach(p => phid(p))
      later(3000 + rnd.nextInt(5000)) {
        phidgetsUp = true
        attachPayloads.foreach(p => phid(p))
      }
    }
  }

  // ── Phidgets : événements de l'API (un par ligne, sans horodatage : l'adaptateur le pose) ──
  private def phid(payload: String): Unit = {
    println(s"${Instant.now} PHIDGETS $payload")
    phidgetCh.write((payload + "\n").getBytes(StandardCharsets.UTF_8))
  }

  private def onPhidgetConnect(): Unit = {
    // les attach arrivent à l'ouverture des canaux
    if (phidgetsUp) attachPayloads.foreach(p => phid(p))
  }

  private def humidityTick(): Unit = {
    if (phidgetsUp) {
      val v = hum + rnd.nextGaussian() * 0.05
      if (lastHum.isNaN || math.abs(v - lastHum) >= 0.1) { // change trigger = 0.1 %
        lastHum = v
        phid(s"HumiditySensor $HumId " + Fmt.f("humidity=%.2f", v))
      }
    }
  }

  private def scaleTick(): Unit = {
    // réponse mécanique légèrement sous-amortie : la valeur oscille avant de se stabiliser
    vel = vel * 0.6 + (targetG - shownG) * 0.3
    shownG += vel
    if (phidgetsUp) {
      val ratio = offset + ScaleGain * shownG + rnd.nextGaussian() * 5e-8
      if (lastRatio.isNaN || math.abs(ratio - lastRatio) >= 2e-7) { // change trigger
        lastRatio = ratio
        phid(s"VoltageRatioInput $ScaleId " + Fmt.f("voltageRatio=%.9f", ratio))
      }
    }
  }

  private def rfidTick(): Unit = {
    if (phidgetsUp && tagOnReader.isEmpty && rnd.nextInt(15) == 0) {
      val tag = if (rnd.nextInt(6) == 0) UnknownTag else KnownTags(rnd.nextInt(KnownTags.size))
      tagOnReader = Some(tag)
      phid(s"RFID $RfidId event=tag tag=$tag proto=EM4100")
      later(1000 + rnd.nextInt(2000)) {
        if (tagOnReader.contains(tag)) {
          tagOnReader = None
          phid(s"RFID $RfidId event=tagLost tag=$tag")
        }
      }
    }
  }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Usage :
//    sbt simulator/run                           Arduino sur :9001, Phidgets sur :9002
//    sbt "simulator/run --scenario canicule"     température qui monte vers 38 °C
//    sbt "simulator/run --scenario chaos"        pannes 5x plus fréquentes + câble arraché
//    sbt "simulator/run --baud 115200 --arduino-port 9001 --phidget-port 9002"
// ─────────────────────────────────────────────────────────────────────────────
@main def simulator(args: String*): Unit = {
  val opts = args.sliding(2, 2).collect { case Seq(k, v) => k -> v }.toMap
  val sim = new Simulation(
    opts.getOrElse("--scenario", "normal"),
    opts.getOrElse("--arduino-port", "9001").toInt,
    opts.getOrElse("--phidget-port", "9002").toInt,
    opts.getOrElse("--baud", "9600").toInt
  )
  sim.start()
}