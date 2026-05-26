# DarwinCore Datapackage Analyser Service

A lightweight service that consumes Darwin Core Data Package (DwC-DP) download
events from RabbitMQ, validates the archive, and publishes a result message.

## Overview

```
RabbitMQ [dwcdp-validator]
  → unzip archive from disk
  → validate with DwcDpPackageAnalyser
  → RabbitMQ [crawler / crawl.dwcdp.validation.finished]
```

The service is triggered by a message (typically published by the GBIF crawler after
a successful archive download). It resolves the zip, unpacks it, runs validation,
and emits a result message regardless of whether validation passed or failed.

## Requirements

- Java 17
- Maven 3.8+
- RabbitMQ 3.x

## Building

```bash
mvn package
```

**Produces 2 packages:**

- API (publish model) at `dwc-dp-analyser-service-api/target/dwc-dp-analyser-service-api-0.0.2-SNAPSHOT.jar`
- Fat jar for service at `dwc-dp-analyser-service-app/target/dwc-dp-analyser-service-app-0.0.2-SNAPSHOT-runner.jar`

## Running

```bash
java -jar dwc-dp-analyser-service-app/target/dwc-dp-analyser-service-app-0.0.2-SNAPSHOT-runner.jar \
  --archive-repository /path/to/datapackages \
  --unpack-repository /path/to/datapackages-unpacked \
  --rabbit-host rabbitmq.example.com \
  --rabbit-user guest \
  --rabbit-password guest \
  --rabbit-vhost / \
  --input-queue dwcdp-validator \
  --output-exchange crawler \
  --output-routing-key crawl.dwcdp.validation.finished
```

All options:

| Option                 | Default                           | Description                                             |
|------------------------|-----------------------------------|---------------------------------------------------------|
| `--archive-repository` | *(required)*                      | Directory where downloaded zip archives are stored      |
| `--unpack-repository`  | *(required)*                      | Directory where archives are unpacked before validation |
| `--rabbit-host`        | `localhost`                       | RabbitMQ host                                           |
| `--rabbit-port`        | `5672`                            | RabbitMQ port                                           |
| `--rabbit-user`        | `guest`                           | RabbitMQ username                                       |
| `--rabbit-password`    | `guest`                           | RabbitMQ password                                       |
| `--rabbit-vhost`       | `/`                               | RabbitMQ virtual host                                   |
| `--input-queue`        | `dwcdp.download.finished`         | Queue to consume from                                   |
| `--output-exchange`    | `crawler`                         | Exchange to publish results to                          |
| `--output-routing-key` | `crawl.dwcdp.validation.finished` | Routing key for published results                       |

## Docker

### Building the image

```bash
task docker:build
```

Or with a specific tag:

```bash
IMAGE=${repo-url}/dwc-dp-analyser:1.0.0 task docker:build
```

### Running the image

`ARCHIVE_REPOSITORY` and `UNPACK_REPOSITORY` are required to know where you are mounting from.
All RabbitMQ options fall back to their defaults if not set.

```bash
ARCHIVE_REPOSITORY=$HOME/data/archives \
UNPACK_REPOSITORY=$HOME/data/unpacked \
RABBIT_HOST=rabbit.test.example.com \
task docker:run
```

### Build jar + image in one shot

```bash
IMAGE=${repo-url}/dwc-dp-analyser:1.0.0 task dist
```

## Kubernetes

The Helm chart requires the following to exist in the cluster before installing:

### RabbitMQ credentials secret

```bash
kubectl create secret generic rabbit-credentials \
  --from-literal=username=<user> \
  --from-literal=password=<password>
```

The secret name must match `rabbit.credentialsSecret` in your `values.yaml` (default: `rabbit-credentials`).

### Installing the chart

```bash
helm install dwc-dp-analyser ./helm/dwc-dp-analyser -f values.yaml
```

## Message format

### Inbound — `dwcdp-validator`

Other fields are accepted and ignored, as long as `datasetUuid` and `attempt` are present.

```json
{
  "datasetUuid": "4fa7b334-ce0d-4e88-aaae-2e0c138d049e",
  "attempt": 3
}
```

### Outbound — `crawler / crawl.dwcdp.validation.finished`

Always published after validation completes. Check `valid` to determine the outcome.

```json
{
  "datasetUuid": "4fa7b334-ce0d-4e88-aaae-2e0c138d049e",
  "attempt": 3,
  "valid": true,
  "validationReport": {}
}
```

If the service cannot process a message (unparseable JSON, unexpected error), it is nacked to the dead-letter queue and no outbound message is published.

## Archive layout

The service resolves archives from `{archive-repository}/{datasetUuid}/` using the following logic:

1. If `datapackage.json` is already present in the directory, use it directly (no unpacking)
2. Otherwise, locate `{datasetUuid}.{attempt}.dwcdp` and unpack it to:
   `{unpack-repository}/{datasetUuid}/{datasetUuid}.{attempt}/`

The `--unpack-repository` option is only used when unpacking is necessary.

## Running tests

```bash
mvn test
```

Tests use `InMemoryMessageBus` — no RabbitMQ instance required.
