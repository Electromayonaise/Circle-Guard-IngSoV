#!/usr/bin/env bash
# Builds the custom Jenkins image and pushes it to ACR.
# Run this once before deploying Jenkins to AKS, and again after any Dockerfile change.
# Usage: bash jenkins/build-and-push.sh
set -euo pipefail

ACR="circleguarddevacr.azurecr.io"
IMAGE="$ACR/jenkins:latest"

echo "==> Logging in to ACR..."
az acr login --name circleguarddevacr

echo "==> Building Jenkins image..."
docker build -f jenkins/Dockerfile -t "$IMAGE" .

echo "==> Pushing to ACR..."
docker push "$IMAGE"

echo "==> Done. Image available at: $IMAGE"
