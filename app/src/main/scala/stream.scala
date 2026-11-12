import io.reactivex.rxjava3.core.{Observable, Observer}
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.disposables.Disposable

case class FakeReading(id : String, value: Int, time: Int)

class ReadingBuffer(capacity: Int) extends Observable[FakeReading]:
  private val buffer = new Array[FakeReading](capacity)
  private var stored = 0
  private val subject = PublishSubject.create[FakeReading]()


  def push(reading: FakeReading): Unit =
    subject.onNext(reading)


// the observer
class ReadingObserver extends Observer[FakeReading]:
  override def onSubscribe(d: Disposable): Unit = println("subscribed")
  override def onNext(r: FakeReading): Unit = println(s"got ${r.id}: ${r.value} at ${r.time}")
  override def onError(e: Throwable): Unit = println(s"error: ${e.getMessage}")
  override def onComplete(): Unit = println("complete")

@main def run(): Unit =
  val source = ReadingBuffer(10)
  source.subscribe(ReadingObserver())

  source.push(FakeReading("s1", 21, 1))
  source.push(FakeReading("s2", 25, 2))