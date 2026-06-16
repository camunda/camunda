#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

usage() {
  cat <<'EOF'
Usage: newLoadTest.sh [--target-version|-t <version>] [options] <namespace>

Options:
  --target-version|-t <version>    Version-specific setup to use. Default: main.
  -h                               Show this help message.

All other options are forwarded to the target version's newLoadTest.sh.
For version-specific help, run: ./newLoadTest.sh --target-version main -h

Available versions: main
EOF
}

target_version="main"
remaining_args=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --target-version|-t)
      if [[ -z "${2-}" ]]; then
        echo "Error: $1 requires an argument." >&2
        usage
        exit 1
      fi
      target_version="$2"
      shift 2
      ;;
    --help)
      usage
      exit 0
      ;;
    *)
      remaining_args+=("$1")
      shift
      ;;
  esac
done

target_script="$SCRIPT_DIR/$target_version/newLoadTest.sh"

if [[ ! -f "$target_script" ]]; then
  echo "Error: No setup found for version '$target_version'." >&2
  available=$(find "$SCRIPT_DIR" -mindepth 2 -maxdepth 2 -name newLoadTest.sh 2>/dev/null \
    | xargs -I{} dirname {} | xargs -I{} basename {} | tr '\n' ' ')
  echo "Available versions: ${available:-none}" >&2
  exit 1
fi

# Run the script in the context of the directory it is in.
cd "$(dirname "$target_script")"
exec "$target_script" "${remaining_args[@]}"
