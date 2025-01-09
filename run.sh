#!/usr/bin/env bash

# 1) Start the slow server on port 9090 in the background
#    (Adjust this command if your slow server is in a separate module, or if you have a different run approach.)
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=9090 \
    -f ../slowserver/pom.xml &

SERVER_PID=$!
echo "Started slow server with PID $SERVER_PID on port 9090"

# 2) Wait a few seconds for server to be fully started
sleep 5

# 3) Build and run the JMH benchmarks (in a different module, or the same if that's how your project is structured)
mvn clean package -DskipTests
echo "Running JMH benchmarks..."

java -jar benchmarks.jar \
  -bm thrpt \
  -t 30 \            # 30 threads
  -wi 3 -i 5 \       # 3 warmup, 5 measurement iterations
  -w 5s -r 10s \     # 5s warmup iteration, 10s measurement iteration
  -f 1

java -jar benchmarks.jar -bm thrpt -t 30 -wi 3 -i 5 -w 5s -r 10s -f 1
# 4) Stop the slow server
echo "Killing slow server..."
kill $SERVER_PID
