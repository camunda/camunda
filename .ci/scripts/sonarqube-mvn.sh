#!/bin/bash
set -ef -o pipefail

PROPERTIES=("-DskipTests -Dcheckstyle.skip")
# Main git branch. Should be updated if main branch changed.
TARGET_BRANCH="master"

# If GITHUB_HEAD_REF defined, then SonarQube runs against a PR.
if [ ! -z "${GITHUB_HEAD_REF}" ]; then
  PROPERTIES+=("-Dsonar.pullrequest.key=${GITHUB_PR_NUMBER}")

  if [ ! -z "${GITHUB_HEAD_REF}" ]; then
    PROPERTIES+=("-Dsonar.pullrequest.branch=${GITHUB_HEAD_REF}")
  fi

  if [ ! -z "${GITHUB_BASE_REF}" ]; then
    PROPERTIES+=("-Dsonar.pullrequest.base=${GITHUB_BASE_REF}")
  fi

# Otherwise, SonarQube runs against a branch.
else
  if [ ! -z "${GITHUB_REF_NAME}" ]; then
    PROPERTIES+=("-Dsonar.branch.name=${GITHUB_REF_NAME}")

    # Don't add sonar.branch.target if branch is the main branch (master).
    if [ "${GITHUB_REF_NAME}" != "${TARGET_BRANCH}" ]; then
      PROPERTIES+=("-Dsonar.branch.target=${TARGET_BRANCH}")
    fi
  fi
fi

echo "Properties: ${PROPERTIES[@]}"
mvn -P sonar sonar:sonar ${PROPERTIES[@]}
