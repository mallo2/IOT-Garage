import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.PublishSubject

import java.util.concurrent.TimeUnit
import scala.util.Random

case class FakeReading(id: String, value: Double, time: Int)

object FakeUdpStream:

  val subject = PublishSubject.create[FakeReading]()
  private val sensorIds = Vector("TempSensor", "ParticularSensor")

  // Simule l'arrivée de paquets UDP : chaque tick pousse un reading dans le subject
  def start(): Unit =
    Observable
      .interval(500, TimeUnit.MILLISECONDS)
      .subscribe { _ =>
        // tout est calculé à chaque tick, pas une seule fois
        val sensorType = sensorIds(Random.nextInt(sensorIds.length))

        val value = sensorType match
          case "TempSensor"       => Random.between(0.0, 35.0)
          case "ParticularSensor" => Random.between(50.0, 600.0)
          case _                  => -4000000.0

        subject.onNext(
          FakeReading(
            id    = sensorType,
            value = value,
            time  = (System.currentTimeMillis() / 1000).toInt
          )
        )
      }

  def stop(): Unit = subject.onComplete()