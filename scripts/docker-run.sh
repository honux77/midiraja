#!/bin/bash
# scripts/docker-run.sh
# Build and run the project inside a Linux container using Colima

IMAGE_NAME="midiraja-linux-builder"

# 1. Build the Docker image if it doesn't exist
docker build -t $IMAGE_NAME -f Dockerfile.linux .

# 2. Run the command inside the container, mounting the source code
docker run --rm -i \
    -v "$(pwd)":/app \
    $IMAGE_NAME "$@"
