# IOT-Garage

## Simulator and Listener

The simulator replaces the hardware during development: an **Arduino MKR 1000** (Wi-Fi) and a **Phidgets hub** (humidity, scale, RFID). Each device is exposed as a TCP server that the application listens to. The provided listener (`Listen`) is used to quickly check the output from the simulator.

### How to run

```
sbt simulator/run                                  # Normal scenario
sbt "simulator/run --scenario canicule"            # Temperature rising to 38 °C
sbt "simulator/run --scenario chaos"               # Failures 5 times more frequent
sbt "simulator/run --arduino-port 9001 --phidget-port 9002"

sbt app/run                                        # listener (localhost:9001 et :9002)
sbt "app/run --arduino 192.168.1.20:9001 --phidget 192.168.1.20:9002"
```