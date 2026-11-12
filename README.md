# IOT-Garage

## Simulator and Listener

The simulator replaces the hardware during development: an **Arduino MKR 1000** (Wi-Fi) and a **Phidgets hub** (humidity, scale, RFID). Each device is exposed through a network server that the application can communicate with using either **TCP or UDP**.

The provided listener (`Listen`) is used to quickly check and monitor the output from the simulator, with automatic reconnection for TCP and datagram reception for UDP.

### How to run

```bash
sbt simulator/run                                  # Normal scenario
sbt "simulator/run --scenario canicule"            # Temperature rising to 38 °C
sbt "simulator/run --scenario chaos"               # Failures 5 times more frequent
sbt "simulator/run --arduino-port 9001 --phidget-port 9002"
sbt "simulator/run --arduino-port 9001 --arduino-protocol udp --phidget-port 9002 --phidget-protocol udp"

sbt app/run                                        # Listener (TCP by default)
sbt "app/run --protocol tcp"                       # TCP communication
sbt "app/run --protocol udp"                       # UDP communication
sbt "app/run --arduino 192.168.1.20:9001 --phidget 192.168.1.20:9002 --protocol udp"
```