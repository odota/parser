#!/bin/bash

if [ -n "$DOCKER_USERNAME" ]; then
  docker login -u="$DOCKER_USERNAME" -p="$DOCKER_PASSWORD"
  docker tag yasp/parser:latest yasp/parser:${TRAVIS_COMMIT}
  docker push yasp/parser:${TRAVIS_COMMIT}
  docker push yasp/parser:latest
fi