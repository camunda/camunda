#!/usr/bin/env bash
set -euo pipefail

# === Configuration ===
# Expected environment variables:
# - RELEASE_VERSION (e.g., "8.6.1")
# - IS_PATCH ("true" or "false")

echo "Updating zeebe.version and identity.version in optimize/pom.xml"
cd optimize

# --- Log current versions ---
CURRENT_ZEEBE=$(./mvnw help:evaluate -Dexpression=zeebe.version -q -DforceStdout)
CURRENT_IDENTITY=$(./mvnw help:evaluate -Dexpression=identity.version -q -DforceStdout)
echo "Current zeebe.version: ${CURRENT_ZEEBE}"
echo "Current identity.version: ${CURRENT_IDENTITY}"

# --- Determine Identity version ---
if [ "${IS_PATCH:-false}" = "true" ]; then
  echo "PATCH release detected - fetching latest Identity patch version from Docker Hub"

  # Extract major.minor (e.g., 8.6.1 -> 8.6)
  MAJOR_MINOR=$(echo "${RELEASE_VERSION}" | sed -E 's/^([0-9]+\.[0-9]+)\.[0-9]+.*$/\1/')

  echo "Fetching latest Identity patch version for ${MAJOR_MINOR}.x from Docker Hub"
  IDENTITY_LATEST_PATCH=$(curl -s "https://registry.hub.docker.com/v2/repositories/camunda/identity/tags/?page_size=1000" \
    | jq -r '.results[].name' \
    | grep -E "^${MAJOR_MINOR}\.[0-9]+$" \
    | sort -V \
    | tail -1)

  if [ -n "${IDENTITY_LATEST_PATCH}" ] && [ "${IDENTITY_LATEST_PATCH}" != "null" ]; then
    echo "Found latest Identity patch version: ${IDENTITY_LATEST_PATCH}"
    IDENTITY_VERSION="${IDENTITY_LATEST_PATCH}"
  else
    echo "No Identity patch version found on Docker Hub, using RELEASE_VERSION"
    IDENTITY_VERSION="${RELEASE_VERSION}"
  fi
else
  echo "Non-patch release - using same version as release"
  IDENTITY_VERSION="${RELEASE_VERSION}"
fi

# --- Update Zeebe and identity versions ---
./mvnw versions:set-property \
  -Dproperty=zeebe.version \
  -DnewVersion="${RELEASE_VERSION}" \
  -DgenerateBackupPoms=false \
  -q
./mvnw versions:set-property \
  -Dproperty=identity.version \
  -DnewVersion="${IDENTITY_VERSION}" \
  -DgenerateBackupPoms=false \
  -q

# --- Log updated versions ---
echo "Updated zeebe.version: $(./mvnw help:evaluate -Dexpression=zeebe.version -q -DforceStdout)"
echo "Updated identity.version: $(./mvnw help:evaluate -Dexpression=identity.version -q -DforceStdout)"

# --- Commit if changed ---
git add pom.xml
if ! git diff --staged --quiet; then
  git commit -m "chore: update zeebe.version to ${RELEASE_VERSION} and identity.version to ${IDENTITY_VERSION}"
  echo "Dependency versions updated and committed."
else
  echo "No version changes needed."
fi
