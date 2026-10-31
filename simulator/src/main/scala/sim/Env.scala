package sim

import java.time.Instant
import java.util.concurrent.{Executors, TimeUnit}
import scala.util.Random

final class Env(val config: Config) {
  val rnd = new Random()
  private val startedAt = System.currentTimeMillis
  private val sched = Executors.newSingleThreadScheduledExecutor()

  def scenario: Scenario = config.scenario
  def chaos: Int = scenario.chaos

  def nowMs: Long = System.currentTimeMillis
  def elapsedSec: Double = (nowMs - startedAt) / 1000.0
  
  def oneIn(n: Int): Boolean = rnd.nextInt(math.max(1, n)) == 0
  def gauss(sigma: Double): Double = rnd.nextGaussian() * sigma
  def pick[A](xs: IndexedSeq[A]): A = xs(rnd.nextInt(xs.size))
  
  def every(ms: Long)(body: => Unit): Unit = {
    sched.scheduleAtFixedRate(task(body), 0, ms, TimeUnit.MILLISECONDS)
    ()
  }

  def later(ms: Long)(body: => Unit): Unit = {
    sched.schedule(task(body), ms, TimeUnit.MILLISECONDS)
    ()
  }

  private def task(body: => Unit): Runnable = () =>
    try body
    catch { case e: Exception => log(s"erreur dans une tâche : $e") }
  
  def log(msg: String): Unit = System.err.println(s"[SIM] $msg")
  def trace(source: String, payload: String): Unit = println(s"${Instant.now} $source $payload")

  def shutdown(): Unit = { sched.shutdownNow(); () }
}