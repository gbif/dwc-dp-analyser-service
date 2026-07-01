#!/bin/sh
set -eu

echo "Waiting for RabbitMQ topology..."

until curl -s -f -u "${RABBIT_USER}:${RABBIT_PASSWORD}" \
  "http://${RABBIT_HOST}:15672/api/queues/%2F/dwcdp-validator" > /dev/null 2>&1; do
  echo "  queue dwcdp-validator not ready yet..."
  sleep 3
done

until curl -s -f -u "${RABBIT_USER}:${RABBIT_PASSWORD}" \
  "http://${RABBIT_HOST}:15672/api/exchanges/%2F/crawler" > /dev/null 2>&1; do
  echo "  exchange crawler not ready yet..."
  sleep 3
done

echo "Topology ready, starting analyser."
exec /entrypoint.sh
