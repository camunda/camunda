#!/bin/bash -xeu

if [ -x "$(which gh >/dev/null 2>&1)" ]; then
  echo "Could not find the GitHub CLI utility; make sure to install it before calling this script"
  echo "See https://github.com/cli/cli/blob/trunk/docs/install_linux.md for more on how to install it"
  exit 1
fi

# Prepare environment, omitting sensitive information
set +x; export GITHUB_TOKEN=${GITHUB_TOKEN_PSW}; set -x
declare -a ASSETS=()
declare -a GH_OPTIONS=()

# This is the list of base artifacts that we expect to upload as part of the release; we'll also
# compute their sha1 sum and upload those as well, but no need to list that here. If there are new
# binaries to ship with a Zeebe release, add them here.
declare -a ARTIFACTS=( \
  "dist/target/camunda-zeebe-${RELEASE_VERSION}.tar.gz" \
  "dist/target/camunda-zeebe-${RELEASE_VERSION}.zip" \
  "clients/go/cmd/zbctl/dist/zbctl" \
  "clients/go/cmd/zbctl/dist/zbctl.exe" \
  "clients/go/cmd/zbctl/dist/zbctl.darwin" \
)

# Prepare assets
CHECKSUM_DIR=$(mktemp -d)
if [ ! -e "${CHECKSUM_DIR}" ]; then
    >&2 echo "Failed to create temporary assets directory directory"
    exit 1
fi
for artifact in "${ARTIFACTS[@]}"; do
  filename=$(basename "${artifact}")
  checksum="${CHECKSUM_DIR}/${filename}.sha1sum"

  if [ ! -f "${artifact}" ]; then
    echo "Expected to upload asset ${artifact} as part of the GitHub release, but no such file was found; are you sure the release went through correctly?"
    exit 1
  fi

  sha1sum "${artifact}" > "${checksum}"
  sha1sumResult=$?
  if [ ! -f "${checksum}" ]; then
    echo "Failed to created checksum of artifact ${artifact} at ${checksum}; [sha1sum] exited with result ${sha1sumResult}. Check the logs for errors."
    exit 1
  fi

  ASSETS+=("${artifact}" "${checksum}")
done

# Mark as pre-release already if it's an alpha or rc
shopt -s nocasematch # set matching to case insensitive
if [[ "${RELEASE_VERSION}" =~ ^.*-(alpha|rc|SNAPSHOT)[\d]*$ ]]; then
  GH_OPTIONS+=(--prerelease)
fi
shopt -u nocasematch # reset it

# Perform release: create draft, upload assets, etc.
# See https://cli.github.com/manual/gh_release_create for more
gh release create --repo "camunda-cloud/zeebe" --draft --notes "Release ${RELEASE_VERSION}" \
  --title "${RELEASE_VERSION}" "${GH_OPTIONS[@]}" \
  "${RELEASE_VERSION}" "${ASSETS[@]}"
