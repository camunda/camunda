#!/bin/bash

GO_OS=${GO_OS:-"linux"}

function detect_os {
    # Detect the OS name
    case "$(uname -s)" in
      Darwin)
        host_os=darwin
        ;;
      Linux)
        host_os=linux
        ;;
      *)
        echo "Unsupported host OS. Must be Linux or Mac OS X." >&2
        exit 1
        ;;
    esac

   GO_OS="${host_os}"
}


function sed_inplace() {
  detect_os

  if [ "${GO_OS}" == "darwin" ]; then
    sed -i '' -e $@
  else
    sed -i -e $@
  fi

}

# Sanitize a string to be a valid Kubernetes label value (max 63 chars, alphanumeric/.-_)
sanitize_k8s_label() {
  local value="$1"
  value=$(echo "$value" | sed 's/[^A-Za-z0-9_.-]/-/g')
  value=$(echo "$value" | sed -E 's/^[^A-Za-z0-9]+//')
  value=${value:0:63}
  value=$(echo "$value" | sed -E 's/[^A-Za-z0-9]+$//')
  if [ -z "$value" ]; then
    value="unknown"
  fi
  echo "$value"
}

compute_git_author() {
  local raw
  raw=$(git config user.name 2>/dev/null || echo "unknown")
  sanitize_k8s_label "$raw"
}

# Pick a "random" zone, selected from the input value.
function hashmod_zone() {
    local input="${1?"Specify an initial value to compute the zone from"}"

    # We can get the list of zones with already created nodes with:
    # kubectl get nodes -o jsonpath='{range .items[*]}{.metadata.labels.topology\.kubernetes\.io\/zone}{"\n"}{end}' | sort | uniq -c
    zones=(
        europe-west1-b
        europe-west1-c
        europe-west1-d
    )
    nb_zones=${#zones[@]}

    # bc only accept hexadecimal with capitalized letters
    checksum="$(echo "$input" | md5sum | cut -c 1-32 | tr "a-z" "A-Z")"
    hashmod="$(echo "ibase=16; $checksum % $nb_zones" | bc)"

    zone="${zones[$hashmod]}"
    echo "$zone"
}

function get_existing_secret() {
  jsonObject=$1
  key=$2
  existing_secret=$(echo "$jsonObject" | jq --raw-output --arg key "$key" '.[$key] // empty' | base64 -d)
  if [ -z "$existing_secret" ]; then
    echo "ERROR: existing camunda-credentials secret is missing key '$key'."
    exit 1
  fi
  echo "$existing_secret"
}

# Generate credentials. These are baked into resources/camunda-credentials.yaml
# and (for the orchestration OIDC secret) into load-test-values.yaml. Any
# subsequent `make install` reapplies the same manifest, so the secret in the
# cluster always matches the value the load test starter authenticates with.
# `head -c 20` closes the pipe early; upstream `tr` then takes SIGPIPE and
# returns 141 under `set -o pipefail`. Wrap in a subshell so the harmless
# SIGPIPE doesn't trip `set -e` in the caller.
function gen_password() { ( set +o pipefail; LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom | head -c 20 ); }
function gen_token()    { openssl rand -hex 16; }
