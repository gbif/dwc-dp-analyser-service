# DarwinCore Datapackage Analyser Service

A lightweight service that consumes Darwin Core Data Package (DwC-DP) download
events from RabbitMQ, validates the archive, stores the validation report in the
GBIF Registry, and publishes a result message.

## Overview

```
RabbitMQ [dwcdp-validator]
→ resolve datapackage (already unpacked, or unzip from archive)
→ validate with DwcDpPackageAnalyser
→ PUT validation report to GBIF Registry
→ RabbitMQ [crawler / crawl.dwcdp.validation.finished]
```

The service is triggered by a message (typically published by the GBIF crawler after
a successful archive download). It resolves the datapackage, unpacking it if necessary,
runs validation, stores the report in the registry, and emits a result message regardless
of whether validation passed or failed. A registry store failure is logged and does not
prevent the outbound message from being published.

## Requirements

- Java 17
- Maven 3.8+
- RabbitMQ 3.x

## Building

```bash
mvn package
```

**Produces 2 packages:**

- API (publish model) at `dwc-dp-analyser-service-api/target/dwc-dp-analyser-service-api-*-SNAPSHOT.jar`
- Fat jar for service at `dwc-dp-analyser-service-app/target/dwc-dp-analyser-service-app-*-SNAPSHOT-runner.jar`

## Running

```bash
java -jar dwc-dp-analyser-service-app/target/dwc-dp-analyser-service-app-*-SNAPSHOT-runner.jar \
  --archive-repository /path/to/datapackages \
  --workdir /path/to/datapackages-unpacked \
  --rabbit-host rabbitmq.example.com \
  --rabbit-user guest \
  --rabbit-password guest \
  --rabbit-vhost / \
  --input-queue dwcdp-validator \
  --output-exchange crawler \
  --output-routing-key crawl.dwcdp.validation.finished \
  --registry-url https://api.gbif-dev.org/v1 \
  --registry-user <user> \
  --registry-password <password>
```

All options:

| Option                   | Default                           | Description                                             |
|--------------------------|-----------------------------------|-----------------------------------------------------------|
| `--archive-repository`   | *(required)*                      | Directory where downloaded archives/datapackages live    |
| `--workdir`              | *(required)*                      | Directory where archives are unpacked before validation  |
| `--rabbit-host`          | `localhost`                       | RabbitMQ host                                             |
| `--rabbit-port`          | `5672`                            | RabbitMQ port                                             |
| `--rabbit-user`          | `guest`                           | RabbitMQ username                                         |
| `--rabbit-password`      | `guest`                           | RabbitMQ password                                         |
| `--rabbit-vhost`         | `/`                               | RabbitMQ virtual host                                     |
| `--input-queue`          | `dwcdp-validator`                 | Queue to consume from                                      |
| `--output-exchange`      | `crawler`                         | Exchange to publish results to                            |
| `--output-routing-key`   | `crawl.dwcdp.validation.finished` | Routing key for published results                         |
| `--registry-url`         | *(required)*                      | Registry API base URL (e.g. `https://api.gbif-dev.org/v1`) |
| `--registry-user`        | *(required)*                      | Registry API username (must have `ADMIN_ROLE`)             |
| `--registry-password`    | *(required)*                      | Registry API password                                      |

## Docker

### Building the image

```bash
task docker:build
```

Or with a specific tag:

```bash
IMAGE=${repo-url}/dwc-dp-analyser:1.0.0 task docker:build
```

By default the build uses the Maven `~/.m2` cache mount as-is. To force a refresh of
`-SNAPSHOT` dependencies (`mvn -U`), set `MAVEN_UPDATE_SNAPSHOTS=true`:

```bash
MAVEN_UPDATE_SNAPSHOTS=true task docker:build
```

### Running the image

`ARCHIVE_REPOSITORY`, `WORKDIR`, `REGISTRY_URL`, `REGISTRY_USER`, and
`REGISTRY_PASSWORD` are required. All RabbitMQ options fall back to their defaults
if not set.

```bash
ARCHIVE_REPOSITORY=$HOME/data/archives \
WORKDIR=$HOME/data/workdir \
RABBIT_HOST=rabbit.test.example.com \
REGISTRY_URL=https://api.gbif-dev.org/v1 \
REGISTRY_USER=<user> \
REGISTRY_PASSWORD=<password> \
task docker:run
```

### Build jar + image in one shot

```bash
IMAGE=${repo-url}/dwc-dp-analyser:1.0.0 task dist
```

## Local development with Docker Compose

For quick local testing, a `docker-compose.yml` is provided with RabbitMQ, a mock
Registry server, an init container that declares the RabbitMQ topology, and the
analyser service itself.

```bash
task local
```

This starts:

- **`rabbitmq`** — RabbitMQ with the management UI at `http://localhost:15672` (guest/guest)
- **`rabbitmq-init`** — declares the `dwcdp-validator` queue, the `crawler` topic exchange,
  and a `crawl.dwcdp.validation.finished` queue bound to it (so outbound messages are
  visible in the management UI)
- **`registry-mock`** — a minimal Python HTTP server that logs every `PUT` it receives
  to stdout, standing in for the real GBIF Registry
- **`analyser`** — the service itself, built from the local `Dockerfile`, waiting for
  the RabbitMQ topology to be ready before starting

### Publishing a test message

Use the helper script to publish a validation request — see
[`scripts/README.md`](scripts/README.md) for full details.

```bash
./scripts/send-validation-request.sh
```

### Observing results

```bash
# Follow all service logs
docker compose logs -f   # or: podman-compose logs -f

# Registry mock only — shows the full validation report JSON
docker compose logs -f registry-mock

# RabbitMQ management UI
open http://localhost:15672
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

### Registry credentials secret

```bash
kubectl create secret generic registry-credentials \
  --from-literal=username=<user> \
  --from-literal=password=<password>
```

The secret name must match `registry.credentialsSecret` in your `values.yaml` (default: `registry-credentials`).

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

### Validation report — GBIF Registry

After validation completes, the full `DatapackageAnalysisResult` is stored via:

```
PUT {registry-url}/dataset/{datasetKey}/validationreport/{attempt}
```

A failure to store the report is logged and does not block the outbound RabbitMQ message.

## Archive layout

The service resolves the datapackage for a given `datasetUuid`/`attempt` by checking
the following locations in order, using the first match:

1. `{archive-repository}/{datasetUuid}/{datasetUuid}.{attempt}/datapackage.json`
   — already unpacked, for this specific attempt
2. `{archive-repository}/{datasetUuid}/datapackage.json`
   — already unpacked, "latest" layout (no attempt-specific subdirectory)
3. `{archive-repository}/{datasetUuid}/{datasetUuid}.{attempt}/{datasetUuid}.{attempt}.dwcdp`
   — archive, unpacked to `{workdir}/{datasetUuid}/{datasetUuid}.{attempt}/`
4. `{archive-repository}/{datasetUuid}/{datasetUuid}.{attempt}/{datasetUuid}.{attempt}.zip`
   — archive, unpacked the same way as above

If none of the four are found, validation fails with an error listing all checked paths.

The `--workdir` option is only used when unpacking is necessary (cases 3 and 4),
and the unpacked directory is deleted after validation completes.

## Running tests

```bash
mvn test
```

Tests use `InMemoryMessageBus` — no RabbitMQ instance required.
