# Build stage
FROM third-party/maven:3-eclipse-temurin-17 AS build
WORKDIR /build
COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -U verify

# Run stage
FROM third-party/eclipse-temurin:17-jre
LABEL authors="gbif"

RUN useradd -r -s /bin/false stackable
WORKDIR /app
RUN mkdir -p /app/.tmp && chown stackable /app/.tmp
COPY --chown=stackable --from=build /build/dwc-dp-analyser-service-app/target/*-runner.jar /app/service.jar

ENV RABBIT_HOST=localhost \
    RABBIT_PORT=5672 \
    RABBIT_USER=guest \
    RABBIT_PASSWORD=guest \
    RABBIT_VHOST=/ \
    IN_QUEUE=dwcdp.download.finished \
    OUT_QUEUE=dwcdp.validation.result \
    JVM_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Xms256m -Xmx2g"

USER stackable

ENTRYPOINT ["sh", "-c", "exec java \
  ${JVM_OPTIONS} \
  -jar /app/service.jar \
  --archive-repository /data/datapackages \
  --unpack-repository /data/datapackages-unpacked \
  --rabbit-host ${RABBIT_HOST} \
  --rabbit-port ${RABBIT_PORT} \
  --rabbit-user ${RABBIT_USER} \
  --rabbit-password ${RABBIT_PASSWORD} \
  --rabbit-vhost ${RABBIT_VHOST} \
  --in-queue ${IN_QUEUE} \
  --out-queue ${OUT_QUEUE}"]
