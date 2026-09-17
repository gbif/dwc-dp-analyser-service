# syntax=docker/dockerfile:1.4

# Default to the GBIF internal "third-party" mirror used in CI/CD.
# Override for local builds, for example:
#   docker build --build-arg BASE_REGISTRY=docker.io ...
ARG BASE_REGISTRY=third-party

FROM ${BASE_REGISTRY}/eclipse-temurin:17-jre

ARG JAR_FILE

LABEL authors="gbif"

RUN useradd -r -s /bin/false stackable

WORKDIR /app
RUN mkdir -p /app/.tmp && chown stackable /app/.tmp

COPY --chown=stackable ${JAR_FILE} /app/service.jar
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
