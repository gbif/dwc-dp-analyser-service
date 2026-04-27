# DarwinCore Datapackage Analyser Service

A lightweight service that consumes Darwin Core Data Package (DwC-DP) download
events from RabbitMQ, validates the archive, and publishes a message if valid

## Overview

```
RabbitMQ [dwcdp.download.finished]
  → unzip archive from disk
  → validate with DwcDpPackageAnalyser
  → RabbitMQ [dwcdp.validation.result]
```

The service is triggered by a `DwcDpDownloadFinishedMessage` published by the GBIF crawler after
a successful archive download. It resolves the zip, unpacks it, runs validation,
and emits a result message.

## Requirements

- Java 17
- Maven 3.8+
- RabbitMQ 3.x

## Building

```bash
mvn package
```

**Produces 2 packages:**

- Api (model for publish model) at `dwc-dp-analyser-service-api/target/dwc-dp-analyser-service-api-0.0.1-SNAPSHOT.jar`
- Fat jar for service at `dwc-dp-analyser-service-app/target/dwc-dp-analyser-service-app-0.0.1-SNAPSHOT-runner.jar`

## Running

```bash
java -jar target/dwcdp-validator-1.0-SNAPSHOT.jar \
  --archive-repository /path/to/datapackages \
  --unpack-repository /path/to/datapackages-unpacked \
  --rabbit-host rabbitmq.example.com \
  --rabbit-user guest \
  --rabbit-password guest \
  --rabbit-vhost / \
  --in-queue dwcdp.download.finished \
  --out-queue dwcdp.validation.result
```

All options:

| Option                 | Default                   | Description                                             |
|------------------------|---------------------------|---------------------------------------------------------|
| `--archive-repository` | *(required)*              | Directory where downloaded zip archives are stored      |
| `--unpack-repository`  | *(required)*              | Directory where archives are unpacked before validation |
| `--rabbit-host`        | `localhost`               | RabbitMQ host                                           |
| `--rabbit-port`        | `5672`                    | RabbitMQ port                                           |
| `--rabbit-user`        | `guest`                   | RabbitMQ username                                       |
| `--rabbit-password`    | `guest`                   | RabbitMQ password                                       |
| `--rabbit-vhost`       | `/`                       | RabbitMQ virtual host                                   |
| `--in-queue`           | `dwcdp.download.finished` | Queue to consume from                                   |
| `--out-queue`          | `dwcdp.validation.result` | Queue to publish results to                             |

## Docker

### Building the image

```bash
task docker:build
```

Or with a specific tag:

```bash
IMAGE=ghcr.io/gbif/dwc-dp-analyser:1.0.0 task docker:build
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
IMAGE=ghcr.io/gbif/dwc-dp-analyser:1.0.0 task dist
```

## Message format

### Inbound — `dwcdp.download.finished`

_Other formats accepted, as long as `datasetUuid` and `attempt` is present_

```json
{
  "datasetUuid": "4fa7b334-ce0d-4e88-aaae-2e0c138d049e",
  "attempt": 3
}
```

### Outbound — `dwcdp.validation.result`

```json
{
  "datasetUuid": "4fa7b334-ce0d-4e88-aaae-2e0c138d049e",
  "attempt": 3
}
```

Only published if validation passes. Failed validations are logged and the message is nacked to the dead-letter queue.

## Archive layout

The service expects archives on disk at:

```
{archive-repository}/{datasetUuid}/{datasetUuid}.{attempt}.ddwcdp
```

And unpacks to:

```
{unpack-repository}/{datasetUuid}/{datasetUuid}.{attempt}/
```

### Running tests

```bash
mvn test
```

Tests use `InMemoryMessageBus` — no RabbitMQ instance required.
