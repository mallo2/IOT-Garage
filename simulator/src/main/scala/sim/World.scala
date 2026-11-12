package sim

import scala.math.{sin}

final class World(env: Env) {
  var temp = 22.0
  var hum = 45.0
  var lux = 300.0
  var pm25 = 8.0

  var loadG = 0.0
  var scaleG = 0.0
  var scaleOffset = 3.1e-5 
  private var vel = 0.0

  private val Loads = Vector(80.0, 250.0, 500.0, 1200.0)

  def install(): Unit = {
    env.every(1000)(stepSlow())
    env.every(200)(stepScale())
  }

  private def stepSlow(): Unit = {
    val t = env.elapsedSec
    val targetTemp = env.scenario.fixedTemp.getOrElse(22.0 + 2 * sin(t / 300))
    temp += (targetTemp - temp) * 0.01 + env.gauss(0.04)
    hum = (hum + (75 - 1.4 * temp - hum) * 0.02 + env.gauss(0.15)).max(5).min(95)
    lux = (50 + 450 * (0.5 + 0.5 * sin(t / 120)) + env.gauss(5)).max(0)
    pm25 = (8 + (pm25 - 8) * 0.92 + env.gauss(0.3)).max(0)
    if (env.oneIn(60)) pm25 += 40 + env.rnd.nextInt(60)
    scaleOffset += env.gauss(2e-9)

    if (loadG == 0 && env.oneIn(25)) loadG = env.pick(Loads)
    else if (loadG > 0 && env.oneIn(12)) loadG = 0
  }

  private def stepScale(): Unit = {
    vel = vel * 0.6 + (loadG - scaleG) * 0.3
    scaleG += vel
  }
}
