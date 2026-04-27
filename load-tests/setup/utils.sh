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

function namespace_name() {
  local namespace="$1"

  # Add c8- prefix if not present
  if [[ ! "$namespace" =~ ^c8- ]]; then
    namespace="c8-$namespace"
  fi
  echo "$namespace"
}


function new_deadline_date() {
  local ttl_days="$1"
  local value="unknown"
  if value=$(date -d "+${ttl_days} days" +%Y-%m-%d 2>/dev/null); then
    # GNU date
    :
  elif value=$(date -v +${ttl_days}d +%Y-%m-%d 2>/dev/null); then
    # BSD/macOS date
    :
  else
    echo "Warning: Could not calculate deadline date. Supported on Linux and macOS only." >&2
  fi
  echo "$value"
}

# Sanitize a string to be a valid Kubernetes label value
sanitize_k8s_label() {
  local value="$1"
  # Replace invalid characters with hyphens
  value=$(echo "$value" | sed 's/[^A-Za-z0-9_.-]/-/g')
  # Remove leading non-alphanumeric characters
  value=$(echo "$value" | sed 's/^[^A-Za-z0-9]\+//')
  # Remove trailing non-alphanumeric characters
  value=$(echo "$value" | sed 's/[^A-Za-z0-9]\+$//')
  # Truncate to 63 characters as required by Kubernetes label values
  value=${value:0:63}
  # Fallback if the result is empty
  if [ -z "$value" ]; then
    value="unknown"
  fi
  echo "$value"
}

function validate_secondary_storage() {
  local value="$1"
  case "$value" in
    elasticsearch|opensearch|postgresql|mysql|mariadb|mssql|oracle|none)
      return 0
      ;;
    *)
      echo "Error: Invalid secondary storage type '$value'." >&2
      echo "Allowed values are: elasticsearch, opensearch, postgresql, mysql, mariadb, mssql, oracle, none" >&2
      return 1
      ;;
  esac
}

function validate_ttl() {
  local value="$1"
  if ! [[ "$value" =~ ^[0-9]+$ ]]; then
    echo "Error: TTL '$value' is not a valid number." >&2
    return 1
  fi
}

# Pick a "random" zone, selected from the input value.
function hashmod_zone() {
  local value="$1"

  # We can get the list of zones with already created nodes with:
  # kubectl get nodes -o jsonpath='{range .items[*]}{.metadata.labels.topology\.kubernetes\.io\/zone}{"\n"}{end}' | sort | uniq -c
  local zones=(
    europe-west1-b
    europe-west1-c
    europe-west1-d
  )
  local nb_zones=${#zones[@]}

  # bc only accept hexadecimal with capitalized letters
  local checksum="$(echo "$value" | md5sum | cut -c 1-32 | tr "a-z" "A-Z")"
  local hashmod="$(echo "ibase=16; $checksum % $nb_zones" | bc)"

  local zone="${zones[$hashmod]}"
  echo "$zone"
}
