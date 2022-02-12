#!/bin/bash

if [ -n "$DOCKER_USERNAME" ]; then
  echo "$DOCKER_PASSWORD" | docker login -u "$DOCKER_USERNAME" --password-stdin
  docker tag odota/parser:latest
  docker push odota/parser:latest
fi
