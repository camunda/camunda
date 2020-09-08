#!/bin/bash
set -ef -o pipefail

PROPERTIES=("-DskipTests -Dcheckstyle.skip")
GIT_URL=${GIT_URL:-$(git remote get-url origin)}
GIT_BRANCH=${GIT_BRANCH:-$(git rev-parse --abbrev-ref HEAD)}
# Main git branch. Should be updated if main branch changed.
TARGET_BRANCH="master"

# If CHANGE_ID defined, then SonarQube runs against a PR.
# Note: This assumes that PRs discovery is enabled in Jenkins.
if [ ! -z "${CHANGE_ID}" ]; then
  PROPERTIES+=("-Dsonar.pullrequest.key=${CHANGE_ID}")

  if [ ! -z "${CHANGE_BRANCH}" ]; then
    PROPERTIES+=("-Dsonar.pullrequest.branch=${CHANGE_BRANCH}")
  fi

  if [ ! -z "${CHANGE_TARGET}" ]; then
    PROPERTIES+=("-Dsonar.pullrequest.base=${CHANGE_TARGET}")
    # SonarCloud requires to have both branches in refs/heads to compare them.
    git fetch --no-tags "${GIT_URL}" "+refs/heads/${CHANGE_TARGET}:refs/remotes/origin/${CHANGE_TARGET}"
  fi

# Otherwise, SonarQube runs against a branch.
else
  if [ ! -z "${GIT_BRANCH}" ]; then
    PROPERTIES+=("-Dsonar.branch.name=${GIT_BRANCH}")

    # Don't add sonar.branch.target if branch is the main branch (master).
    if [ "${GIT_BRANCH}" != "${TARGET_BRANCH}" ]; then
      PROPERTIES+=("-Dsonar.branch.target=${TARGET_BRANCH}")
    fi
  fi
  # SonarCloud requires to have both branches in refs/heads to compare them.
  git fetch --no-tags "${GIT_URL}" "+refs/heads/${TARGET_BRANCH}:refs/remotes/origin/${TARGET_BRANCH}"
fi

echo "Properties: ${PROPERTIES[@]}"
mvn -s $MAVEN_SETTINGS_XML -P sonar sonar:sonar ${PROPERTIES[@]}
