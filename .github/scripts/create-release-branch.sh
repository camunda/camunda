#!/bin/bash

# fail fast and print each statement
set -ex

if [[ -z "$GITHUB_ACTOR" ]]; then
  echo "GITHUB_ACTOR is required"
  exit 1
fi

if [[ -z "$GITHUB_ACTOR_ID" ]]; then
  echo "GITHUB_ACTOR_ID is required"
  exit 1
fi

if [[ -z "$GITHUB_APP_PRIVATE_KEY" ]]; then
  echo "GITHUB_APP_PRIVATE_KEY is required"
  exit 1
fi

if [[ -z "$RELEASE_VERSION" ]]; then
  echo "RELEASE_VERSION is required"
  exit 1
fi

if [[ -z "$NEXT_DEVELOPMENT_VERSION" ]]; then
  echo "NEXT_DEVELOPMENT_VERSION is required"
  exit 1
fi

git config --global user.name "${GITHUB_ACTOR}"
git config --global user.email "${GITHUB_ACTOR_ID}+${GITHUB_ACTOR}@users.noreply.github.com"

# For minor and major releases, we need to create a release branch in the examples repository
if [[ "$RELEASE_TYPE" == "minor" || "$RELEASE_TYPE" == "major" ]]; then
  git remote set-url origin https://$GITHUB_ACTOR_ID:$GITHUB_APP_PRIVATE_KEY@github.com/camunda/camunda-optimize-examples.git
  git fetch
  git checkout master
  git checkout -b release/$RELEASE_VERSION
  git push origin release/$RELEASE_VERSION
fi

git remote set-url origin "https://${GITHUB_ACTOR_ID}:${GITHUB_APP_PRIVATE_KEY}@github.com/camunda/camunda-optimize.git"
git fetch
git checkout master
# We need to pull with rebase here because we changed the remote from examples to optimize main repo and we need to override the changes
git pull --rebase
mvn -B gitflow:release-start -DpushRemote=true -DdevelopmentVersion="${NEXT_DEVELOPMENT_VERSION}-SNAPSHOT" -DreleaseVersion=${RELEASE_VERSION}
