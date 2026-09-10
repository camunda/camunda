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

mkdir -p "${asdf_dir}/bin"
curl -fsSL -o "${asdf_archive}" \
  "https://github.com/asdf-vm/asdf/releases/download/v${ASDF_VERSION}/asdf-v${ASDF_VERSION}-linux-amd64.tar.gz"
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
