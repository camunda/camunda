#!/bin/bash
set -ef -o pipefail

PROPERTIES=("-DskipTests -DskipChecks")
GIT_URL=${GIT_URL:-$(git remote get-url origin)}
GIT_BRANCH=${GIT_BRANCH:-$(git rev-parse --abbrev-ref HEAD)}

# Assume if CHANGE_ID defined that we're building a PR
if [ ! -z "${CHANGE_ID}" ]; then
  PROPERTIES+=("-Dsonar.pullrequest.key=${CHANGE_ID}")

  if [ ! -z "${CHANGE_BRANCH}" ]; then
    PROPERTIES+=("-Dsonar.pullrequest.branch=${CHANGE_BRANCH}")
  fi

  if [ ! -z "${CHANGE_TARGET}" ]; then
    PROPERTIES+=("-Dsonar.pullrequest.base=${CHANGE_TARGET}")
    git fetch --no-tags "${GIT_URL}" "+refs/heads/${CHANGE_TARGET}:refs/remotes/origin/${CHANGE_TARGET}"
  fi
else
  if [ ! -z "${GIT_BRANCH}" ]; then
    PROPERTIES+=("-Dsonar.branch.name=${GIT_BRANCH}")
  fi

  if [ "${GIT_BRANCH}" == "master" ] || [ "${GIT_BRANCH}" == "develop" ]; then
    TARGET_BRANCH="${GIT_BRANCH}"
  else
    TARGET_BRANCH="develop"
    PROPERTIES+=("-Dsonar.branch.target=${TARGET_BRANCH}")
  fi

  git fetch --no-tags "${GIT_URL}" "+refs/heads/${TARGET_BRANCH}:refs/remotes/origin/${TARGET_BRANCH}"
fi

printf "Properties: %s\n" "${PROPERTIES[@]}"
mvn -B -s "${MAVEN_SETTINGS_XML}" -P sonar -PcheckFormat,-autoFormat sonar:sonar "${PROPERTIES[@]}"
