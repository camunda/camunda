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

normalize_load_test_name() {
  echo "$1" | tr '[:upper:]' '[:lower:]'
}

validate_load_test_namespace() {
  local namespace="$1"

  if [[ ! "$namespace" =~ ^[a-z0-9]([-a-z0-9]*[a-z0-9])?$ ]]; then
    echo "Error: namespace '$namespace' is not a valid Kubernetes DNS-1123 label."
    echo "       Allowed: lowercase letters, digits, '-'. Must start and end with an alphanumeric."
    return 1
  fi
  if [ ${#namespace} -gt 63 ]; then
    echo "Error: namespace '$namespace' is ${#namespace} characters; Kubernetes labels are capped at 63."
    return 1
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
