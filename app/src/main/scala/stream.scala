import io.reactivex.rxjava3.core.{Observer}
import io.reactivex.rxjava3.disposables.Disposable


// the observer
class ReadingObserver extends Observer[FakeReading]:
  override def onSubscribe(d: Disposable): Unit = println("subscribed")
  override def onNext(r: FakeReading): Unit = println(s"got ${r.id}: ${r.value} at ${r.time}")
  override def onError(e: Throwable): Unit = println(s"error: ${e.getMessage}")
  override def onComplete(): Unit = println("complete")

@main def run(): Unit =
  FakeUdpStream.start()
  val source= FakeUdpStream.subject
  source.subscribe(ReadingObserver())
  Thread.sleep(5000)
