#!/bin/bash -e

IS_M2RELEASEBUILD=$1
POM_VERSION=$2

IMAGE_NAME="dwc-dp-analyser-service"
IMAGE="docker.gbif.org/${IMAGE_NAME}:${POM_VERSION}"
IMAGE_LATEST="docker.gbif.org/${IMAGE_NAME}:latest"
BASE_REGISTRY="docker.gbif.org/third-party"

./build/docker-image.sh "${IMAGE}" "${BASE_REGISTRY}"

echo "Pushing Docker image to the repository"
docker push "${IMAGE}"

if [[ ${IS_M2RELEASEBUILD} = true ]]; then
  echo "Updating latest tag to point to ${IMAGE}"
  docker tag "${IMAGE}" "${IMAGE_LATEST}"
  docker push "${IMAGE_LATEST}"
fi

echo "Removing local Docker image: ${IMAGE}"
docker rmi -f "${IMAGE}"

if [[ ${IS_M2RELEASEBUILD} = true ]]; then
  echo "Removing local Docker image with latest tag: ${IMAGE_LATEST}"
  docker rmi -f "${IMAGE_LATEST}"
fi
