# scripts/send-validation-request.sh

Helper script for local testing. Publishes a DwC-DP validation request message to
RabbitMQ, triggering the analyser service as if the crawler had done so.

## Prerequisites

- `curl` — used to publish via the RabbitMQ management API
- `python3` — used to JSON-escape the message payload
- `fzf` — optional, required only for the interactive picker mode

The local stack must be running:

```bash
docker compose up   # or: podman-compose up
```

## Usage

```bash
# Interactive: pick an archive/folder with fzf
./scripts/send-validation-request.sh

# Explicit path, default archive root (./data/datapackages)
./scripts/send-validation-request.sh /path/to/4fa7b334-.../4fa7b334-....3.dwcdp

# Explicit path, custom archive root
./scripts/send-validation-request.sh /path/to/4fa7b334-....3.dwcdp /mnt/data/datapackages
```

## Accepted input formats

The path can point to any of the following, matching the analyser's own resolution
priority:

1. `<root>/<uuid>/<uuid>.<attempt>/datapackage.json` — already unpacked, this attempt
2. `<root>/<uuid>/<uuid>.<attempt>/` — already unpacked directory (pass the directory itself)
3. `<root>/<uuid>/<uuid>.<attempt>/<uuid>.<attempt>.dwcdp` — archive
4. `<root>/<uuid>/<uuid>.<attempt>/<uuid>.<attempt>.zip` — archive

Example:

```
./data/datapackages/
└── 4fa7b334-ce0d-4e88-aaae-2e0c138d049e/
    └── 4fa7b334-ce0d-4e88-aaae-2e0c138d049e.3/
        ├── 4fa7b334-ce0d-4e88-aaae-2e0c138d049e.3.dwcdp
        └── datapackage.json        # if already unpacked
```

If the path is not already under the archive root, the script copies it into the
correct location before publishing — `cp` for files, `cp -r` for directories (replacing
any stale copy at the destination first). If it is already there, the copy is skipped.

## Configuration

All options can be overridden via environment variables:

| Variable           | Default              | Description                        |
|--------------------|-----------------------|------------------------------------|
| `RABBIT_HOST`      | `localhost`           | RabbitMQ host                      |
| `RABBIT_PORT`      | `5672`                | RabbitMQ AMQP port                 |
| `RABBIT_USER`      | `guest`                | RabbitMQ username                  |
| `RABBIT_PASSWORD`  | `guest`                | RabbitMQ password                  |
| `RABBIT_VHOST`     | `/`                     | RabbitMQ virtual host              |
| `INPUT_QUEUE`      | `dwcdp-validator`       | Queue to publish the message to    |

Example with overrides:

```bash
RABBIT_HOST=rabbit.internal INPUT_QUEUE=dwcdp-validator-dev \
  ./scripts/send-validation-request.sh ./data/datapackages/4fa7b334-.../4fa7b334-....3.dwcdp
```

## What it does

1. Resolves the given path to one of the four accepted forms and validates it against
   the expected `<uuid>/<uuid>.<attempt>` structure
2. Copies it to the archive root if not already present
3. Publishes the following message to the configured queue via the RabbitMQ management
   API (port 15672), URL-encoding the vhost (`/` → `%2F`) as required by the API:

```json
{
  "datasetUuid": "4fa7b334-ce0d-4e88-aaae-2e0c138d049e",
  "attempt": 3
}
```

4. Checks the response for `"routed":true` and reports an error if the message wasn't
   routed (e.g. the queue doesn't exist or isn't bound)

The analyser picks up the message, resolves the datapackage, runs validation, logs the
registry `PUT` to the `registry-mock` container, and publishes a result to
`crawler / crawl.dwcdp.validation.finished`.

## Observing results

```bash
# Follow all service logs
docker compose logs -f   # or: podman-compose logs -f

# Registry mock only — shows the full validation report JSON
docker compose logs -f registry-mock

# RabbitMQ management UI
open http://localhost:15672
```
