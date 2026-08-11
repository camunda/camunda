#!/usr/bin/env bash
# Scene 7b - supply the missing secret.
#
# The file store reads files on every call, so a secret added (or rotated) after startup is picked
# up without restarting the cluster.

source "$(dirname "${BASH_SOURCE[0]}")/env.sh"

printf 'pay-live-DEMO-77c1e8' > "$SECRET_DEMO_DIR/missingToken"
chmod 600 "$SECRET_DEMO_DIR/missingToken"

ls -l "$SECRET_DEMO_DIR"
