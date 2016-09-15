#!/bin/bash

if [ -n "$DOCKER_USERNAME" ]; then
  docker login -u="$DOCKER_USERNAME" -p="$DOCKER_PASSWORD"
  docker tag odota/parser:latest odota/parser:${TRAVIS_COMMIT}
  docker push odota/parser:${TRAVIS_COMMIT}
  docker push odota/parser:latest
fi
