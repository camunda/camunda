#!/bin/bash -eu
#
# Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
# one or more contributor license agreements. See the NOTICE file distributed
# with this work for additional information regarding copyright ownership.
# Licensed under the Zeebe Community License 1.1. You may not use this file
# except in compliance with the Zeebe Community License 1.1.
#
# Usage: ./release.sh [-p] VERSION
# Example usage:
#   $ ./release.sh 1.2.4
# This script will build the Docker images for the worker and starter applications in this project,
# for the given version. The `VERSION` should be the semantic version, and match the tag that you
# want to build. The script will checkout that tag in a temporary worktree, and run the docker
# commands from that worktree.
#
# By default, the script is interactive, and it will ask the user whether or not to push the images.
# You can specify the `-p` flag to automatically push, or the environment variable PUSH=1, e.g.:
#   $ ./release.sh -p 1.2.4

WORKTREE=$(mktemp -d)
if [ ! -e "${WORKTREE}" ]; then
    >&2 echo "Failed to create worktree directory"
    exit 1
fi

function cleanup() {
  if [ -d "${WORKTREE}" ]; then
    popd > /dev/null 2>&1
	  echo "Cleaning up release worktree at ${WORKTREE}"
	  git worktree remove -f "${WORKTREE}"
	  rm -rf "${WORKTREE}"
  fi
}
trap cleanup INT TERM EXIT HUP

function killChildren()  {
  local children
  children="$(jobs -p)"

  if [ -n "${children}" ]; then
    echo "${children}" | xargs kill -TERM
  fi
}

# Ensure this runs in a subshell so we have our own trap function
pushImages() {
  trap killChildren INT TERM HUP
  docker push "gcr.io/zeebe-io/starter:$TAG" 1>/dev/null &
  docker push "gcr.io/zeebe-io/worker:$TAG" 1>/dev/null &
	wait
}

if [ "$#" -eq "0" ]; then
  echo "Usage: ./release.sh [-p] VERSION"
  echo "For example, to release version 1.2.4:"
  echo "  $ ./release.sh 1.2.4"
  echo "This will build and ask the user whether to push or not"
  echo "If you wish to push without being asked, use the \`-p\` flag. e.g.:"
  echo "  $ ./release.sh -p 1.2.4"
  echo "This will build and push automatically without any user interaction"
  exit 0
fi

if [ "${1:-}" == "-p" ]; then
  PUSH=1
  shift
fi

VERSION=${1:-}
if [ -n "$VERSION" ]; then
	echo "Checking out release worktree under ${WORKTREE}. In case of interruption, make sure to manually clean it up afterwards"
	git worktree add -q "${WORKTREE}" "${VERSION}"
	pushd "${WORKTREE}" > /dev/null 2>&1
fi

echo "Building benchmark project"
./mvnw -B -D skipTests -D skipChecks -am -pl benchmarks/project package

TAG=${VERSION:-SNAPSHOT}
echo "Building gcr.io/zeebe-io/starter:${TAG}"
./mvnw -B -D skipTests -D skipChecks -D image="gcr.io/zeebe-io/starter:${TAG}" -P starter -pl benchmarks/project jib:buildDocker

echo "Building gcr.io/zeebe-io/worker:${TAG}"
./mvnw -B -D skipTests -D skipChecks -D image="gcr.io/zeebe-io/worker:${TAG}" -P worker -pl benchmarks/project jib:buildDocker

PUSH=${PUSH:-0}
if [ "${PUSH}" -ne "1" ]; then
	read -p "Push images? (y/n) " -n 1 -r
  echo

	if [[ $REPLY =~ ^[Yy]$ ]]
	then
		PUSH=1
	fi
fi

if [ "$PUSH" -eq "1" ]; then
	echo "Pushing image gcr.io/zeebe-io/starter:$TAG and gcr.io/zeebe-io/worker:$TAG"
  (pushImages)
else
	echo "Skipping push..."
fi
