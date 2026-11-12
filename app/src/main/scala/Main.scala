@main def main(): Unit =
  FakeUdpStream.start()
  val source= FakeUdpStream.subject
  source.subscribe(StreamObserver())
  Thread.sleep(5000)
