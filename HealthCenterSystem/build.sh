#!/usr/bin/env sh
# Compiles the app and tests, runs the logic tests, and rebuilds HealthCenterSystem.jar.
# Needs a JDK (javac and jar). Run from the project root: sh build.sh
set -e
cd "$(dirname "$0")"
rm -rf build && mkdir build
javac -encoding UTF-8 --release 8 -d build \
  src/Main.java src/Components/*.java src/Authentication/*.java src/Data/*.java src/Patient/*.java \
  src/QueueManager/*.java src/QueueSimulation/*.java src/Store/*.java
javac -encoding UTF-8 --release 8 -cp build -d build \
  tests/QueueManagerTest.java tests/BackupRecoveryTest.java tests/DequeueTest.java
java -cp build QueueManagerTest
java -cp build BackupRecoveryTest
java -cp build DequeueTest
javac -encoding UTF-8 --release 8 -cp build -d build tests/HoldNextTest.java
java -cp build HoldNextTest
printf 'Main-Class: Main\n' > manifest.tmp
jar cfm HealthCenterSystem.jar manifest.tmp -C build Main.class \
  -C build Authentication -C build Components -C build Data -C build Patient \
  -C build QueueManager -C build QueueSimulation -C build Store
rm -f manifest.tmp
echo "Built HealthCenterSystem.jar. Run it with: java -jar HealthCenterSystem.jar"
