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
