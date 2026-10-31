package sim

import java.util.Locale

object Fmt {
  def f(fmt: String, x: Double): String = fmt.formatLocal(Locale.ROOT, x)
}

final case class Scenario(name: String, chaos: Int = 1, fixedTemp: Option[Double] = None)

object Scenario {
  val Normal   = Scenario("normal")
  val Canicule = Scenario("canicule", fixedTemp = Some(38.0))
  val Chaos    = Scenario("chaos", chaos = 5)

  val all: Map[String, Scenario] = Vector(Normal, Canicule, Chaos).map(s => s.name -> s).toMap

  def parse(name: String): Scenario =
    all.getOrElse(name, throw new IllegalArgumentException(s"Scénario inconnu : $name (${all.keys.mkString(", ")})"))
}

final case class Config(scenario: Scenario = Scenario.Normal, arduinoPort: Int = 9001, phidgetPort: Int = 9002)

object Config {
  def parse(args: Seq[String]): Config = {
    val o = args.sliding(2, 2).collect { case Seq(k, v) => k -> v }.toMap
    val d = Config()
    Config(
      scenario    = o.get("--scenario").map(Scenario.parse).getOrElse(d.scenario),
      arduinoPort = o.get("--arduino-port").map(_.toInt).getOrElse(d.arduinoPort),
      phidgetPort = o.get("--phidget-port").map(_.toInt).getOrElse(d.phidgetPort)
    )
  }
}