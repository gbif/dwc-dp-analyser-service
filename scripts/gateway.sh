#!/usr/bin/env bash

if ! which jq &>/dev/null; then
  echo "Error: jq is required but not installed. Please install it: https://jqlang.org/download/" >&2
  exit 1
fi

CLI=$(which docker 2>/dev/null || which podman 2>/dev/null)
NETWORK=$([[ "$CLI" == *podman* ]] && echo "podman" || echo "bridge")
$CLI network inspect "$NETWORK" | jq -r '.[0].subnets[0].gateway'
