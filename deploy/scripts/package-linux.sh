#!/usr/bin/env sh
set -eu

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
BUNDLE_NAME="life-service-platform-docker"
OUTPUT_DIR="${ROOT_DIR}/release"
WORK_DIR="${OUTPUT_DIR}/${BUNDLE_NAME}"

rm -rf "${WORK_DIR}"
mkdir -p "${WORK_DIR}"

cp "${ROOT_DIR}/Dockerfile" "${WORK_DIR}/"
cp "${ROOT_DIR}/docker-compose.yml" "${WORK_DIR}/"
cp "${ROOT_DIR}/.dockerignore" "${WORK_DIR}/"

mkdir -p "${WORK_DIR}/target"
cp "${ROOT_DIR}/target/life-service-platform-0.0.1-SNAPSHOT.jar" "${WORK_DIR}/target/"

mkdir -p "${WORK_DIR}/src/main/resources"
cp -r "${ROOT_DIR}/src/main/resources/db" "${WORK_DIR}/src/main/resources/"

mkdir -p "${WORK_DIR}/deploy"
cp -r "${ROOT_DIR}/deploy/frontend" "${WORK_DIR}/deploy/"
cp -r "${ROOT_DIR}/deploy/scripts" "${WORK_DIR}/deploy/"
mkdir -p "${WORK_DIR}/deploy/mysql/data" "${WORK_DIR}/deploy/redis/data" "${WORK_DIR}/deploy/rabbitmq/data" "${WORK_DIR}/deploy/kafka/data"

cp "${ROOT_DIR}/src/main/resources/application-docker.yaml" "${WORK_DIR}/src/main/resources/"

chmod +x "${WORK_DIR}/deploy/scripts/"*.sh

cd "${OUTPUT_DIR}"
tar -czf "${BUNDLE_NAME}.tar.gz" "${BUNDLE_NAME}"
echo "Created: ${OUTPUT_DIR}/${BUNDLE_NAME}.tar.gz"
