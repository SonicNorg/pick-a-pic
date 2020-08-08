#!/bin/bash
echo "$DOCKER_PASSWORD" | docker login -u "$DOCKER_USERNAME" --password-stdin
docker push $DOCKER_USERNAME/pick-a-pic:"`echo $TRAVIS_BRANCH | tr / .`"