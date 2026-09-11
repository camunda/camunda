#!/usr/bin/env bash

set -euo pipefail

: "${ASDF_VERSION:?ASDF_VERSION is required}"
: "${PLUGINS:?PLUGINS is required}"
: "${WORKING_DIRECTORY:?WORKING_DIRECTORY is required}"

cd "${WORKING_DIRECTORY}"

plugins=()
while IFS= read -r plugin; do
  [[ -n "${plugin}" ]] || continue
  if [[ ! "${plugin}" =~ ^[A-Za-z0-9_-]+$ ]]; then
    echo "::error::Invalid asdf plugin name '${plugin}'"
    exit 1
  fi
  plugins+=("${plugin}")
done < <(printf '%s\n' "${PLUGINS}" | tr '[:space:]' '\n')

if (( ${#plugins[@]} == 0 )); then
  echo "::error::At least one asdf plugin is required"
  exit 1
fi

asdf_dir="${HOME}/.asdf"
asdf_archive="$(mktemp)"
trap 'rm -f "${asdf_archive}"' EXIT

asdf_asset="asdf-v${ASDF_VERSION}-linux-amd64.tar.gz"
asdf_release_url="https://github.com/asdf-vm/asdf/releases/download/v${ASDF_VERSION}/${asdf_asset}"

# Hardcoded MD5 for asdf-v0.20.0-linux-amd64.tar.gz (asdf's release-build
# workflow, wangyoucao577/go-release-action, does not opt into SHA256).
#
# This is intentionally NOT fetched from asdf's release the way the archive
# itself is: a checksum published next to the binary it verifies only proves
# the download completed, not that the binary is what asdf actually built —
# an attacker (or compromised release) able to swap the tarball could swap
# the published .md5 alongside it. A value committed to this repo and
# reviewed on every bump is a real integrity check instead.
#
# Must be updated together with the `asdf_version` input default in
# action.yml whenever that version is bumped.
asdf_expected_checksum="b7abaeb5c874dd6c3c59580a84050935"

mkdir -p "${asdf_dir}/bin"
curl -fsSL -o "${asdf_archive}" "${asdf_release_url}"

asdf_actual_checksum="$(md5sum "${asdf_archive}" | cut -d ' ' -f1)"
if [[ "${asdf_actual_checksum}" != "${asdf_expected_checksum}" ]]; then
  echo "::error::asdf archive checksum mismatch for ${asdf_asset}: expected ${asdf_expected_checksum}, got ${asdf_actual_checksum}"
  exit 1
fi

tar -C "${asdf_dir}/bin" -xzf "${asdf_archive}"

echo "${asdf_dir}/bin" >> "${GITHUB_PATH}"
echo "${asdf_dir}/shims" >> "${GITHUB_PATH}"
echo "ASDF_DIR=${asdf_dir}" >> "${GITHUB_ENV}"
echo "ASDF_DATA_DIR=${asdf_dir}" >> "${GITHUB_ENV}"
export PATH="${asdf_dir}/bin:${asdf_dir}/shims:${PATH}"

for plugin in "${plugins[@]}"; do
  if asdf plugin list 2>/dev/null | grep -Fxq "${plugin}"; then
    asdf plugin update "${plugin}"
  else
    asdf plugin add "${plugin}"
  fi
done

asdf install
