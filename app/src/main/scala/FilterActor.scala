import org.apache.pekko.actor.Actor

class FilterActor extends Actor {
  def receive: Receive = {
    case reading:FakeReading if reading.id == "TempSensor" => {
      println(s"TempSensor reading: ${reading.value}")
    }
    case reading: FakeReading if reading.id == "ParticularSensor" =>{
      println(s"ParticularSensor reading: ${reading.value}")
    }
    case other: FakeReading => {
      println(s"ignored ${other.id}")
    }
  }
}