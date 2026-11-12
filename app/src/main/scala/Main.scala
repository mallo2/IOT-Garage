import io.reactivex.rxjava3.core.Observer
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.subjects.PublishSubject
import org.apache.pekko.actor.{ActorRef, ActorSystem, Props}


@main def main(): Unit = {
  FakeUdpStream.start()
  val system = ActorSystem("iot")
  val filter = system.actorOf(Props[FilterActor](), "filter")

  val source= FakeUdpStream.subject
  source.subscribe(StreamObserver(filter))

  Thread.sleep(5000)
  FakeUdpStream.stop()
  system.terminate()
}

