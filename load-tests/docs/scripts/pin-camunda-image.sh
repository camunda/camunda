#!/bin/bash
# Pin the Camunda orchestration image to an immutable digest for this run.
# Manual runs default to the mutable SNAPSHOT tag (pullPolicy: Always), so a pod
# rescheduled mid-test can re-pull a newer SNAPSHOT and drift the engine version.
# Prints a `--set orchestration.image.digest=...` Helm flag to stdout (empty when
# no pin is needed). Diagnostics go to stderr so the caller can capture stdout.
set -eo pipefail

values_file="camunda-platform-values-defaults.yaml"
extra_helm_args="$1" # additional_platform_configuration from the Makefile
image="docker.io/camunda/camunda:SNAPSHOT"

log() { echo "$@" >&2; }

# Skip when an explicit Camunda image is already set via Helm args (e.g. CI's built tag).
if echo "$extra_helm_args" | grep -qE 'orchestration\.image\.(tag|digest)|global\.image\.tag'; then
  log "Camunda image set explicitly via Helm args; skipping digest pin."
  exit 0
fi

# Skip when the values file pins a non-SNAPSHOT tag (the "use a different snapshot" flow).
tag_line=$(sed -n '/^orchestration:/,$p' "$values_file" | grep -m1 '^    tag:' || true)
tag=$(echo "${tag_line#*tag:}" | tr -d ' "')
if [ "$tag" != "SNAPSHOT" ]; then
  log "orchestration.image.tag is '$tag' (not SNAPSHOT); skipping digest pin."
  exit 0
fi

# Resolve the current multi-arch digest for the SNAPSHOT tag.
log "Resolving $image digest to pin this run..."
digest=$(docker buildx imagetools inspect "$image" --format '{{.Manifest.Digest}}' 2>/dev/null \
  || crane digest "$image" 2>/dev/null || true)
if [ -z "$digest" ]; then
  log "ERROR: could not resolve digest for $image (need docker buildx imagetools or crane)."
  exit 1
fi

log "Pinning orchestration image to $image@$digest"
echo "--set orchestration.image.digest=$digest"
