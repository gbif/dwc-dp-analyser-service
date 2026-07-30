# syntax=docker/dockerfile:1.4
# Default to the GBIF internal "third-party" mirror used in CI/CD.
# Override for local builds: docker build --build-arg BASE_REGISTRY=docker.io ...
ARG BASE_REGISTRY=third-party
ARG MAVEN_UPDATE_SNAPSHOTS=false

# Build stage
FROM ${BASE_REGISTRY}/maven:3-eclipse-temurin-17 AS build
ARG MAVEN_UPDATE_SNAPSHOTS
WORKDIR /build
COPY . .

RUN --mount=type=cache,target=/root/.m2 \
    if [ "$MAVEN_UPDATE_SNAPSHOTS" = "true" ]; then \
      echo ">> MAVEN_UPDATE_SNAPSHOTS=true — forcing -U"; \
      mvn -U verify; \
    else \
      echo ">> MAVEN_UPDATE_SNAPSHOTS=false — normal verify"; \
      mvn verify; \
    fi

# Run stage
FROM third-party/eclipse-temurin:17-jre
LABEL authors="gbif"

RUN useradd -r -s /bin/false stackable
WORKDIR /app
RUN mkdir -p /app/.tmp && chown stackable /app/.tmp
COPY --chown=stackable --from=build /build/dwc-dp-analyser-service-app/target/*-runner.jar /app/service.jar
COPY --chown=stackable docker/entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

ENV RABBIT_HOST=localhost \
    RABBIT_PORT=5672 \
    RABBIT_USER=guest \
    RABBIT_PASSWORD=guest \
    RABBIT_VHOST=/ \
    DUCKDB_MEMORY="2GiB" \
    DUCKDB_TEMP_DIR="/data/workdir/.tmp" \
    INPUT_QUEUE=dwcdp-validator \
    OUTPUT_EXCHANGE=crawler \
    OUTPUT_ROUTING_KEY=crawl.dwcdp.validation.finished \
    REGISTRY_URL="" \
    REGISTRY_USER="" \
    REGISTRY_PASSWORD="" \
    JVM_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=30.0 -Xms256m -Xmx1g"

USER stackable

ENTRYPOINT ["/entrypoint.sh"]
