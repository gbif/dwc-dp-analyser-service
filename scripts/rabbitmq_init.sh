#!/bin/sh
set -eu

until curl -s -u guest:guest http://rabbitmq:15672/api/overview > /dev/null; do
  echo "Waiting for RabbitMQ..."
  sleep 3
done

curl -fsS -u guest:guest -X PUT http://rabbitmq:15672/api/queues/%2F/dwcdp-validator \
  -H 'Content-Type: application/json' \
  -d '{"durable": true}'
echo "Declared queue: dwcdp-validator"

curl -fsS -u guest:guest -X PUT http://rabbitmq:15672/api/exchanges/%2F/crawler \
  -H 'Content-Type: application/json' \
  -d '{"type": "topic", "durable": true}'
echo "Declared exchange: crawler (topic)"

curl -fsS -u guest:guest -X PUT http://rabbitmq:15672/api/queues/%2F/crawl.dwcdp.validation.finished \
  -H 'Content-Type: application/json' \
  -d '{"durable": true}'

curl -fsS -u guest:guest -X POST http://rabbitmq:15672/api/bindings/%2F/e/crawler/q/crawl.dwcdp.validation.finished \
  -H 'Content-Type: application/json' \
  -d '{"routing_key": "crawl.dwcdp.validation.finished"}'
echo "Declared and bound queue: crawl.dwcdp.validation.finished"

echo "RabbitMQ topology ready."
