#!/bin/bash

if [ -n "$DOCKER_USERNAME" ]; then
  echo "$DOCKER_PASSWORD" | docker login -u "$DOCKER_USERNAME" --password-stdin
  docker tag odota/parser:latest
  docker push odota/parser:latest
  docker tag howardc93/parser:latest
  docker push howard93/parser:latest
fi
