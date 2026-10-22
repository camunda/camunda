# The Event Plane's service, maintenance and demo workers, in one image; the command names the
# main class. Build after `./mvnw -pl zeebe/event-plane/demo -am package -Dquickly` from the root:
#   docker build -t event-plane-tools:trial zeebe/event-plane/demo
FROM eclipse-temurin:21-jre
COPY target/lib/ /app/lib/
COPY target/zeebe-event-plane-demo-*.jar /app/lib/
# Decoding records uses Zeebe's buffers (Agrona), which need jdk.internal.misc; zstd is native.
ENTRYPOINT ["java", "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED", \
  "--enable-native-access=ALL-UNNAMED", "-cp", "/app/lib/*"]
CMD ["io.camunda.zeebe.eventplane.demo.WorkerApp"]
