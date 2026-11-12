/*

import com.phidget22.{PhidgetException, RFID, VoltageRatioInput}
import scala.io.StdIn

@main def rfidMain(): Unit =
  val rfid = new RFID()
  rfid.addTagListener(e => println(s"Tag détecté : ${e.getTag} (${e.getProtocol})"))
  rfid.addTagLostListener(e => println(s"Tag perdu : ${e.getTag}"))
  try
    rfid.open(5000)
    rfid.setAntennaEnabled(true)
    println("Lecteur détecté. Approche un tag, Entrée pour quitter.")
    scala.io.StdIn.readLine()
    ()
  catch
    case e: PhidgetException => println(s"Erreur Phidget : ${e.getMessage}")
  finally
    try rfid.close()
    catch case _: PhidgetException => ()
    
@main def balanceMain(): Unit =
  val loadCell = new VoltageRatioInput()
  def averageReading(samples: Int = 20): Double =
    var total = 0.0
    for _ <- 1 to samples do
      Thread.sleep(50)
      total += loadCell.getVoltageRatio()
    total / samples
  try
    loadCell.setChannel(0)
    loadCell.open(5000)
    loadCell.setBridgeEnabled(true)
    loadCell.setDataInterval(50)
    loadCell.setVoltageRatioChangeTrigger(0.0)
    println("PhidgetBridge connecté.")
    println()
    println("=== Calibration de la balance ===")
    println("Retire tout poids de la balance.")
    println("Appuie sur Entrée lorsque la balance est vide.")
    StdIn.readLine()
    println("Mesure du zéro...")
    val zero = averageReading()
    println(f"Valeur à vide : $zero%.8f")
    println()
    println("Place un poids de référence sur la balance.")
    print("Masse connue en grammes (ex. 500) : ")
    val referenceGrams = StdIn.readLine().trim.toDouble
    require(referenceGrams > 0, "La masse doit être positive.")
    println("Mesure du poids de référence...")
    val loaded = averageReading()
    val difference = loaded - zero
    if math.abs(difference) < 1e-9 then
      throw new IllegalStateException(
        "Signal trop faible : vérifie la cellule et son câblage."
      )
    val gramsPerRatio = referenceGrams / difference
    println(f"Référence mesurée : $loaded%.8f")
    println("Calibration terminée.")
    println()
    println("Retire le poids de référence.")
    println("La balance affiche maintenant les grammes.")
    println("Appuie sur Entrée pour quitter.")
    val reader = new Thread(() =>
      try
        while !Thread.currentThread().isInterrupted do
          val ratio = loadCell.getVoltageRatio()
          val grams = (ratio - zero) * gramsPerRatio
          println(f"Poids : $grams%.2f g")
          Thread.sleep(250)
      catch
        case _: InterruptedException => ()
        case e: PhidgetException =>
          println(s"Erreur de lecture : ${e.getMessage}")
    )
    reader.setDaemon(true)
    reader.start()
    StdIn.readLine()
    reader.interrupt()
  catch
    case e: PhidgetException =>
      println(s"Erreur Phidget : ${e.getMessage}")
    case e: NumberFormatException =>
      println("Erreur : entre une masse numérique, par exemple 500.")
    case e: IllegalArgumentException =>
      println(s"Erreur de calibration : ${e.getMessage}")
    case e: IllegalStateException =>
      println(s"Erreur : ${e.getMessage}")
  finally
    try loadCell.close()
    catch
      case _: PhidgetException => ()
      
 */