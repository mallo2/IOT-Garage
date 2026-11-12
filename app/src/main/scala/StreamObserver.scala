import io.reactivex.rxjava3.core.Observer
import io.reactivex.rxjava3.disposables.Disposable
import org.apache.pekko.actor.ActorRef

// the observer
class StreamObserver(target: ActorRef) extends Observer[FakeReading]:
  override def onSubscribe(d: Disposable): Unit = println("subscribed")
  override def onNext(reading: FakeReading): Unit = target ! reading
  override def onError(e: Throwable): Unit = println(s"error: ${e.getMessage}")
  override def onComplete(): Unit = println("complete")

