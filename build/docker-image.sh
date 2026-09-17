#!/bin/bash -e

IMAGE=$1
BASE_REGISTRY=${2:-docker.gbif.org/third-party}

JAR_DIR="dwc-dp-analyser-service-app/target"
JAR_PATTERN="${JAR_DIR}/*-runner.jar"

mapfile -t JARS < <(compgen -G "${JAR_PATTERN}" || true)

if [[ ${#JARS[@]} -eq 0 ]]; then
  echo >&2 "ERROR: Could not find ${JAR_PATTERN}"
  echo >&2 "Run 'mvn package' before building the Docker image."
  exit 1
fi

if [[ ${#JARS[@]} -gt 1 ]]; then
  echo >&2 "ERROR: Found multiple runner JARs:"
  printf >&2 '  %s\n' "${JARS[@]}"
  echo >&2 "Run 'mvn clean package' to produce an unambiguous build."
  exit 1
fi

JAR_FILE="${JARS[0]}"

echo "Building Docker image: ${IMAGE}"
echo "Using runner JAR: ${JAR_FILE}"
echo "Using base registry: ${BASE_REGISTRY}"

docker build \
  --build-arg BASE_REGISTRY="${BASE_REGISTRY}" \
  --build-arg JAR_FILE="${JAR_FILE}" \
  -t "${IMAGE}" \
  .
