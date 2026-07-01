#!/bin/sh
set -eu

mkdir -p "${DUCKDB_TEMP_DIR}" 2>/dev/null || true

exec java \
  ${JVM_OPTIONS} \
  -jar /app/service.jar \
  --archive-repository /data/datapackages \
  --workdir /data/workdir \
  --rabbit-host "${RABBIT_HOST}" \
  --rabbit-port "${RABBIT_PORT}" \
  --rabbit-user "${RABBIT_USER}" \
  --rabbit-password "${RABBIT_PASSWORD}" \
  --rabbit-vhost "${RABBIT_VHOST}" \
  --duckdb-memory "${DUCKDB_MEMORY}" \
  --duckdb-temp-dir "${DUCKDB_TEMP_DIR}" \
  --input-queue "${INPUT_QUEUE}" \
  --output-exchange "${OUTPUT_EXCHANGE}" \
  --output-routing-key "${OUTPUT_ROUTING_KEY}" \
  --registry-url "${REGISTRY_URL}" \
  --registry-user "${REGISTRY_USER}" \
  --registry-password "${REGISTRY_PASSWORD}"
