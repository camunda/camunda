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

# Retry a command with exponential backoff. Only the invoked command's own
# exit status is inspected (stdout/stderr of every attempt are shown live),
# and on final failure the real exit code from the last attempt is returned
# unchanged, so a permanent/logical error (bad chart name, malformed values)
# still fails clearly, it's just tried max_attempts times first. Guards
# against transient network blips (e.g. a "Get ...: EOF" on a Helm chart
# download) without masking real failures.
# Tuning: set RETRY_MAX_ATTEMPTS/RETRY_BASE_DELAY_SECONDS in the environment;
# defaults below apply when unset. Set RETRY_CLEANUP_CMD to a shell command
# that undoes a failed attempt's partial state before the next one starts
# (e.g. `helm pull --untar` refuses to run again if its target directory is
# already there from a killed-mid-extraction previous attempt).
# Usage: retry_with_backoff <command...>
retry_with_backoff() {
  local max_attempts="${RETRY_MAX_ATTEMPTS:-4}"
  local delay="${RETRY_BASE_DELAY_SECONDS:-5}"
  local attempt=1
  until "$@"; do
    local status=$?
    if (( attempt >= max_attempts )); then
      echo "Command failed after ${attempt} attempt(s), giving up: $*" >&2
      return "$status"
    fi
    echo "Attempt ${attempt}/${max_attempts} failed (exit ${status}); retrying in ${delay}s: $*" >&2
    [[ -n "${RETRY_CLEANUP_CMD:-}" ]] && eval "$RETRY_CLEANUP_CMD"
    sleep "$delay"
    delay=$(( delay * 2 ))
    ((attempt++))
  done
}

# Check whether a value is present in a list (pass the list as trailing args).
# Usage: if contains "$value" "${some_array[@]}"; then ...
contains() {
  local value="$1"
  shift
  local item
  for item in "$@"; do
    [[ "$item" == "$value" ]] && return 0
  done
  return 1
}

# Parse and validate a Kubernetes DNS-1123 namespace label.
# Adds "c8-" prefix if missing, then validates. On success, echoes the final
# namespace name to stdout. Informational messages go to stderr. Exits on error.
parse_namespace() {
  local namespace="$1"
  if [[ ! "$namespace" =~ ^c8- ]]; then
    namespace="c8-$namespace"
    echo "Namespace prefix added: $namespace" >&2
  fi
  if [[ ! "$namespace" =~ ^[a-z0-9]([-a-z0-9]*[a-z0-9])?$ ]]; then
    echo "Error: namespace '$namespace' is not a valid Kubernetes DNS-1123 label." >&2
    echo "       Allowed: lowercase letters, digits, '-'. Must start and end with an alphanumeric." >&2
    exit 1
  fi
  if [ ${#namespace} -gt 63 ]; then
    echo "Error: namespace '$namespace' is ${#namespace} characters; Kubernetes labels are capped at 63." >&2
    exit 1
  fi
  echo "$namespace"
}

# Parse and validate a non-negative integer TTL. Echoes the value on success. Exits on error.
parse_ttl() {
  local ttl="$1"
  if ! [[ $ttl =~ ^[0-9]+$ ]]; then
    echo "Error: TTL '$ttl' is not a number" >&2
    exit 1
  fi
  echo "$ttl"
}

# Parse and validate a non-negative integer count of extra physical tenants
# (pt1..ptN). Echoes the value on success. Exits on error.
parse_physical_tenant_count() {
  local count="$1"
  if ! [[ $count =~ ^[0-9]+$ ]]; then
    echo "Error: physical tenant count '$count' is not a number" >&2
    exit 1
  fi
  echo "$count"
}

# Parse and validate a secondary storage type against the allowed list.
# Usage: storage="$(parse_secondary_storage "$storage" "${allowed_storage[@]}")"
# Echoes the storage value on success. Exits with an error if not in the allowed list.
parse_secondary_storage() {
  local storage="$1"
  shift
  if ! contains "$storage" "$@"; then
    echo "Error: Invalid secondary storage type '$storage'" >&2
    echo "Allowed values are: $(echo "$*" | tr ' ' ',')" >&2
    exit 1
  fi
  echo "$storage"
}

# Parse and normalize a boolean (true/false) argument.
# Usage: value="$(parse_bool "<raw_value>")"
# Lowercases the input, validates it, and echoes the normalized value. Exits on error.
parse_bool() {
  local value
  value=$(echo "$1" | tr '[:upper:]' '[:lower:]')
  if [[ "$value" != "true" && "$value" != "false" ]]; then
    echo "Error: Invalid boolean value '$1'" >&2
    echo "Allowed values are: true or false" >&2
    exit 1
  fi
  echo "$value"
}

# Compute the deadline date TTL_DAYS from now. Echoes the date in YYYY-MM-DD format.
compute_deadline_date() {
  local ttl_days="$1"
  local deadline_date
  if deadline_date=$(date -d "+${ttl_days} days" +%Y-%m-%d 2>/dev/null); then
    : # GNU date succeeded
  elif deadline_date=$(date -v +"${ttl_days}"d +%Y-%m-%d 2>/dev/null); then
    : # BSD/macOS date succeeded
  else
    echo "Error: Could not calculate deadline date. Supported on Linux and macOS only." >&2
    exit 1
  fi
  echo "$deadline_date"
}
