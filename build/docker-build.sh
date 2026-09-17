#!/bin/bash
set -euo pipefail

usage() {
  cat >&2 <<EOF
Usage: $0 <is-release:true|false> <version>

Examples:
  $0 false 0.0.7-SNAPSHOT
  $0 false 0.0.7-FEATURE-TEST-JENKINS-SNAPSHOT
  $0 true  0.0.7
EOF
}

if [[ $# -ne 2 ]]; then
  echo >&2 "ERROR: Invalid docker-build.sh invocation."
  echo >&2 "Expected exactly 2 arguments but received $#."
  if [[ $# -gt 0 ]]; then
    printf >&2 "Received arguments:"
    printf >&2 " <%s>" "$@"
    printf >&2 "\n"
  else
    echo >&2 "Received arguments: <none>"
  fi
  echo >&2
  usage
  exit 2
fi

IS_M2RELEASEBUILD=$1
POM_VERSION=$2

if [[ "${IS_M2RELEASEBUILD}" != "true" && "${IS_M2RELEASEBUILD}" != "false" ]]; then
  echo >&2 "ERROR: Invalid release flag '${IS_M2RELEASEBUILD}'."
  echo >&2 "Expected 'true' or 'false'."
  echo >&2
  usage
  exit 2
fi

if [[ -z "${POM_VERSION//[[:space:]]/}" ]]; then
  echo >&2 "ERROR: Docker image version is empty."
  echo >&2 "The second argument must be the Maven project version."
  echo >&2
  usage
  exit 2
fi

IMAGE_NAME="dwc-dp-analyser-service"
IMAGE="docker.gbif.org/${IMAGE_NAME}:${POM_VERSION}"
IMAGE_LATEST="docker.gbif.org/${IMAGE_NAME}:latest"
BASE_REGISTRY="docker.gbif.org/third-party"

echo "Docker build configuration:"
echo "  release:       ${IS_M2RELEASEBUILD}"
echo "  version:       ${POM_VERSION}"
echo "  image:         ${IMAGE}"
echo "  base registry: ${BASE_REGISTRY}"

# docker-image.sh performs runner-JAR validation and then invokes docker build.
# Any docker/build failure is intentionally not caught here, so Docker's
# original error message and exit code remain visible in Jenkins.
./build/docker-image.sh "${IMAGE}" "${BASE_REGISTRY}"

echo "Pushing Docker image to the repository: ${IMAGE}"
docker push "${IMAGE}"

if [[ "${IS_M2RELEASEBUILD}" == "true" ]]; then
  echo "Updating latest tag to point to ${IMAGE}"
  docker tag "${IMAGE}" "${IMAGE_LATEST}"
  docker push "${IMAGE_LATEST}"
fi

echo "Removing local Docker image: ${IMAGE}"
docker rmi -f "${IMAGE}"

if [[ "${IS_M2RELEASEBUILD}" == "true" ]]; then
  echo "Removing local Docker image with latest tag: ${IMAGE_LATEST}"
  docker rmi -f "${IMAGE_LATEST}"
fi
