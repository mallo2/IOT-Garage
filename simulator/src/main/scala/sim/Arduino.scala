package sim

import java.nio.charset.StandardCharsets.ISO_8859_1
import java.util.Locale

final class Sensor(val periodMs: Long, read: => String) {
  var nextAt = 0L
  def sample(): String = read
}

object ArduinoSensors {
  def default(env: Env, w: World): Vector[Sensor] = Vector(
    new Sensor(500, Fmt.f("LIGHT,%.2f", w.lux.toInt)),
    new Sensor(2000, Fmt.f("TEMP,%.2f", w.temp)),
    new Sensor(5000, if (env.oneIn(50)) "PM,ERR" else s"PM,${w.pm25.toInt},${(w.pm25 * 1.4).toInt}")
  )
}

final class LineNoise(env: Env) {
  def apply(line: String): String = {
    val r = env.rnd.nextInt(1000)
    val c = env.chaos
    if (r < 2 * c && line.length > 1) {
      val i = 1 + env.rnd.nextInt(line.length - 1)
      line.substring(0, i) + (line(i) ^ (1 << env.rnd.nextInt(7))).toChar + line.substring(i + 1)
    } else if (r < 3 * c) "\u00ff" + line
    else line
  }
}

final class ArduinoDevice(env: Env, sensors: Vector[Sensor]) extends Device {
  val name = "arduino"

  private val channel: TcpChannel = new TcpChannel(
    name,
    env.config.arduinoPort,
    onConnect = () => env.later(0)(if (env.nowMs < downUntil) channel.dropClients()),
    onLine = l => env.later(0)(onCommand(l))
  )

  private var downUntil = 0L
  private var booting = false

  def start(): Unit = {
    channel.start()
    env.every(10)(loop())
    env.every(1000)(maybeLoseWifi())
  }

  private def send(payload: String): Unit = {
    env.trace("ARDUINO", payload)
    channel.write((payload + "\r\n").getBytes(ISO_8859_1)) // sans client connecté : perdu
  }

  private def loop(): Unit = {
    val now = env.nowMs
    if (now >= downUntil) {
      if (booting) {
        booting = false
        send("BOOT v1.2")
        send("INFO sensors ready")
        sensors.foreach(_.nextAt = now + 500)
      }
      sensors.foreach { s =>
        if (now >= s.nextAt) {
          s.nextAt = now + s.periodMs + env.rnd.nextInt(5)
          send(s.sample())
        }
      }
      if (env.oneIn(30000 / env.chaos)) goOffline("watchdog / brown-out", 4000 + env.rnd.nextInt(6000), reboot = true)
    }
  }

  private def maybeLoseWifi(): Unit =
    if (env.nowMs >= downUntil && env.oneIn(600 / env.chaos))
      goOffline("perte Wi-Fi", 2000 + env.rnd.nextInt(6000), reboot = false)

  private def goOffline(why: String, ms: Long, reboot: Boolean): Unit = {
    env.log(s"MKR 1000 hors ligne ($why, ${ms} ms)")
    channel.dropClients()
    booting = reboot
    downUntil = env.nowMs + ms
  }

  private def onCommand(raw: String): Unit = {
    val cmd = raw.trim
    env.log(s"<- commande Arduino : $cmd")
    if (cmd.nonEmpty && env.nowMs >= downUntil)
      send("ACK," + cmd.toUpperCase(Locale.ROOT).replace(' ', '_'))
  }

  private def maybeDropCable(): Unit =
    if (env.oneIn(90)) {
      env.log("câble Arduino débranché")
      channel.dropClients()
    }
}