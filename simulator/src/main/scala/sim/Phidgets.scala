package sim

import java.nio.charset.StandardCharsets.UTF_8
import scala.math.abs


abstract class PhidgetChannel(val kind: String, val id: String, val periodMs: Long) {
  def attachEvent: String = s"$kind $id event=attach"
  def detachEvent: String = s"$kind $id event=detach"

  def tick(emit: String => Unit): Unit

  def reset(): Unit = ()
}

final class HumidityChannel(env: Env, world: World)
  extends PhidgetChannel("HumiditySensor", "serial=654321 port=0 ch=0", 250) {
  private var last = Double.NaN

  def tick(emit: String => Unit): Unit = {
    val v = world.hum + env.gauss(0.05)
    if (last.isNaN || abs(v - last) >= 0.1) {
      last = v
      emit(Fmt.f("humidity=%.2f", v))
    }
  }
  override def reset(): Unit = last = Double.NaN
}

final class ScaleChannel(env: Env, world: World)
  extends PhidgetChannel("VoltageRatioInput", "serial=654321 port=1 ch=0", 200) {
  private val Gain = 2.0e-6
  private var last = Double.NaN

  def tick(emit: String => Unit): Unit = {
    val ratio = world.scaleOffset + Gain * world.scaleG + env.gauss(5e-8)
    if (last.isNaN || abs(ratio - last) >= 2e-7) {
      last = ratio
      emit(Fmt.f("voltageRatio=%.9f", ratio))
    }
  }
  override def reset(): Unit = last = Double.NaN
}

final class RfidChannel(env: Env) extends PhidgetChannel("RFID", "serial=432108 port=0", 1000) {
  private val Known = Vector("Tag1", "Tag2", "Tag3", "Tag3")
  private val Unknown = "0100deadbe"
  private var onReader: Option[String] = None

  def tick(emit: String => Unit): Unit =
    if (onReader.isEmpty && env.oneIn(15)) {
      val tag = if (env.oneIn(6)) Unknown else env.pick(Known)
      onReader = Some(tag)
      emit(s"event=tag tag=$tag proto=EM4100")
      env.later(1000 + env.rnd.nextInt(2000)) {
        if (onReader.contains(tag)) {
          onReader = None
          emit(s"event=tagLost tag=$tag")
        }
      }
    }
  override def reset(): Unit = onReader = None
}

final class PhidgetHub(env: Env, channels: Vector[PhidgetChannel]) extends Device {
  val name = "phidget"

  private val channel = new TcpChannel(
    name,
    env.config.phidgetPort,
    onConnect = () => env.later(0)(if (up) attachAll()),
    onLine = _ => ()
  )
  private var up = true

  def start(): Unit = {
    channel.start()
    channels.foreach(c => env.every(c.periodMs)(if (up) c.tick(p => publish(s"${c.kind} ${c.id} $p"))))
    env.every(1000)(maybeDetach())
  }

  private def publish(payload: String): Unit = {
    env.trace("PHIDGETS", payload)
    channel.write((payload + "\n").getBytes(UTF_8))
  }

  private def attachAll(): Unit = channels.foreach(c => publish(c.attachEvent))

  private def maybeDetach(): Unit =
    if (up && env.oneIn(120 / env.chaos)) {
      up = false
      channels.foreach { c => c.reset(); publish(c.detachEvent) }
      env.later(3000 + env.rnd.nextInt(5000)) {
        up = true
        attachAll()
      }
    }
}
