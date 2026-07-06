#!/usr/bin/env bash
set -euo pipefail

IMAGE="thanhthong2005/debezium-connect-postgresql:2.7.3.Final"

cd "$(dirname "${BASH_SOURCE[0]}")"

docker build -t "$IMAGE" .
docker push "$IMAGE"

echo ""
echo "Pushed: $IMAGE"
echo ""
echo "Update values.yaml:"
echo "  image: $IMAGE"
