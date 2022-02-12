#!/bin/bash

if [ -n "$DOCKER_USERNAME" ]; then
  echo "$DOCKER_PASSWORD" | docker login -u "$DOCKER_USERNAME" --password-stdin
  docker tag odota/parser:latest odota/parser:${TRAVIS_COMMIT}
  docker push odota/parser:${TRAVIS_COMMIT}
  docker push odota/parser:latest
fi
