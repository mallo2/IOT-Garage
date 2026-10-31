package sim

final class Simulation(world: World, devices: Seq[Device]) {
  def start(): Unit = {
    world.install()
    devices.foreach(_.start())
  }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Usage :
//    sbt simulator/run                           Arduino MKR 1000 (Wi-Fi) sur :9001, Phidgets sur :9002
//    sbt "simulator/run --scenario canicule"     température qui monte vers 38 °C
//    sbt "simulator/run --scenario chaos"        pannes 5x plus fréquentes (resets, coupures Wi-Fi, Phidgets)
//    sbt "simulator/run --arduino-port 9001 --phidget-port 9002"
// ─────────────────────────────────────────────────────────────────────────────
@main def simulator(args: String*): Unit = {
  val env = new Env(Config.parse(args))
  val world = new World(env)

  val devices = Vector(
    new ArduinoDevice(env, ArduinoSensors.default(env, world)),
    new PhidgetHub(env, Vector(new HumidityChannel(env, world), new ScaleChannel(env, world), new RfidChannel(env)))
  )

  new Simulation(world, devices).start()
}