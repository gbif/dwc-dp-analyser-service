#!/usr/bin/env bash
set -euo pipefail

ARCHIVE_ROOT="${2:-./data/datapackages}"
RABBIT_HOST="${RABBIT_HOST:-localhost}"
RABBIT_PORT="${RABBIT_PORT:-5672}"
RABBIT_USER="${RABBIT_USER:-guest}"
RABBIT_PASSWORD="${RABBIT_PASSWORD:-guest}"
RABBIT_VHOST="${RABBIT_VHOST:-/}"
INPUT_QUEUE="${INPUT_QUEUE:-dwcdp-validator}"

UUID_RE='[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}'

usage() {
  echo "Usage: $0 [<path>] [archive-root]"
  echo ""
  echo "  <path> can be:"
  echo "    <root>/<uuid>/<uuid>.<attempt>.dwcdp"
  echo "    <root>/<uuid>/<uuid>.<attempt>.zip"
  echo "    <root>/<uuid>/<uuid>.<attempt>/                  (unpacked dir)"
  echo "    <root>/<uuid>/<uuid>.<attempt>/datapackage.json"
  echo ""
  echo "  With no path argument, launches fzf to pick from archive-root."
  echo "  archive-root defaults to ./data/datapackages"
  exit 1
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
fi

# --- resolve input: fzf picker or explicit path ---
if [[ -z "${1:-}" ]]; then
  if ! command -v fzf &>/dev/null; then
    echo "Error: no path given and fzf is not installed" >&2
    exit 1
  fi
  INPUT_PATH=$(find "$ARCHIVE_ROOT" -mindepth 2 -maxdepth 2 \( -name "*.dwcdp" -o -name "*.zip" -o -type d \) \
    | fzf --prompt="Select archive/folder: ") || {
    echo "No selection made." >&2
    exit 1
  }
else
  INPUT_PATH="$1"
fi

INPUT_PATH=$(realpath "$INPUT_PATH")

# --- normalise to the uuid.attempt entry, regardless of which of the 3 forms was given ---
if [[ -f "$INPUT_PATH" && "$(basename "$INPUT_PATH")" == "datapackage.json" ]]; then
  # case 3: .../<uuid>.<attempt>/datapackage.json → step up to the uuid.attempt dir
  ENTRY="$(dirname "$INPUT_PATH")"
  IS_DIR=true
elif [[ -d "$INPUT_PATH" ]]; then
  # case 2: .../<uuid>.<attempt>/
  ENTRY="$INPUT_PATH"
  IS_DIR=true
elif [[ -f "$INPUT_PATH" ]]; then
  # case 1: .../<uuid>.<attempt>.dwcdp or .zip
  ENTRY="$INPUT_PATH"
  IS_DIR=false
else
  echo "Error: path does not exist: $INPUT_PATH" >&2
  exit 1
fi

ENTRY_NAME=$(basename "$ENTRY")
PARENT_DIR=$(basename "$(dirname "$ENTRY")")

if [[ "$IS_DIR" == true ]]; then
  if [[ ! "$ENTRY_NAME" =~ ^(${UUID_RE})\.([0-9]+)$ ]]; then
    echo "Error: directory name must match <datasetUuid>.<attempt>, got: $ENTRY_NAME" >&2
    exit 1
  fi
else
  if [[ ! "$ENTRY_NAME" =~ ^(${UUID_RE})\.([0-9]+)\.(dwcdp|zip)$ ]]; then
    echo "Error: filename must match <datasetUuid>.<attempt>.(dwcdp|zip), got: $ENTRY_NAME" >&2
    exit 1
  fi
fi

DATASET_UUID="${BASH_REMATCH[1]}"
ATTEMPT="${BASH_REMATCH[2]}"

if [[ "$PARENT_DIR" != "$DATASET_UUID" ]]; then
  echo "Error: parent directory must match datasetUuid [$DATASET_UUID], got: $PARENT_DIR" >&2
  exit 1
fi

# --- copy to archive root if not already there ---
ARCHIVE_ROOT=$(realpath "$ARCHIVE_ROOT")
EXPECTED_DIR="$ARCHIVE_ROOT/$DATASET_UUID"
EXPECTED_ENTRY="$EXPECTED_DIR/$ENTRY_NAME"

if [[ "$ENTRY" != "$EXPECTED_ENTRY" ]]; then
  echo "Copying $ENTRY → $EXPECTED_ENTRY"
  mkdir -p "$EXPECTED_DIR"
  if [[ "$IS_DIR" == true ]]; then
    rm -rf "$EXPECTED_ENTRY"
    cp -r "$ENTRY" "$EXPECTED_ENTRY"
  else
    cp "$ENTRY" "$EXPECTED_ENTRY"
  fi
else
  echo "Already in archive root, skipping copy."
fi

# --- publish message to RabbitMQ ---
PAYLOAD=$(printf '{"datasetUuid":"%s","attempt":%s}' "$DATASET_UUID" "$ATTEMPT")
ESCAPED_PAYLOAD=$(printf '%s' "$PAYLOAD" | python3 -c 'import json,sys; print(json.dumps(sys.stdin.read()))')

PUBLISH_BODY=$(printf '{
  "properties": {},
  "routing_key": "%s",
  "payload": %s,
  "payload_encoding": "string"
}' "$INPUT_QUEUE" "$ESCAPED_PAYLOAD")

echo "--- DEBUG ---"
echo "RABBIT_HOST=$RABBIT_HOST"
echo "RABBIT_VHOST=$RABBIT_VHOST"
echo "INPUT_QUEUE=$INPUT_QUEUE"
echo "PAYLOAD=$PAYLOAD"
echo "ESCAPED_PAYLOAD=$ESCAPED_PAYLOAD"
echo "PUBLISH_BODY=$PUBLISH_BODY"
echo "URL=http://$RABBIT_HOST:15672/api/exchanges/$RABBIT_VHOST/amq.default/publish"
echo "-------------"

RABBIT_VHOST_ENCODED=$(printf '%s' "$RABBIT_VHOST" | sed 's#/#%2F#g')

RESPONSE=$(curl \
  -u "$RABBIT_USER:$RABBIT_PASSWORD" \
  -H "Content-Type: application/json" \
  -X POST "http://$RABBIT_HOST:15672/api/exchanges/$RABBIT_VHOST_ENCODED/amq.default/publish" \
  -d "$PUBLISH_BODY" 2>&1)

echo "Response: $RESPONSE"

if echo "$RESPONSE" | grep -q '"routed":true'; then
  echo ""
  echo "Done — dataset [$DATASET_UUID] attempt [$ATTEMPT] queued."
else
  echo ""
  echo "Error: message was not routed. Check that queue [$INPUT_QUEUE] exists and is bound." >&2
  exit 1
fi
