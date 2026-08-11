#!/usr/bin/env bash
# Scene 1 - create the file-based secret store.
#
# One file per secret: the file name is the secret name, the file contents are the value. This is
# exactly how Kubernetes projects a mounted Secret volume, so the same directory works unchanged
# with k8s, External Secrets Operator, or the Secrets Store CSI driver.

source "$(dirname "${BASH_SOURCE[0]}")/env.sh"

mkdir -p "$SECRET_DEMO_DIR"
chmod 700 "$SECRET_DEMO_DIR"

printf 'sk-live-DEMO-9f3ad41c' > "$SECRET_DEMO_DIR/apiToken"
printf 'p4ssw0rd-from-the-store' > "$SECRET_DEMO_DIR/dbPassword"

# Accepted by the file store but NOT a valid secret reference name: a reference is
# camunda.secrets.<name> with name limited to alphanumerics and underscores, so the dot in
# "tls.crt" rules it out. It must not appear in the listing endpoint's output.
printf -- '-----BEGIN CERTIFICATE-----\nDEMO\n-----END CERTIFICATE-----' > "$SECRET_DEMO_DIR/tls.crt"

chmod 600 "$SECRET_DEMO_DIR"/*

# Marks this directory as created by the demo. run-demo.sh refuses to delete a secrets directory
# without it, so a mistyped SECRET_DEMO_DIR can never take a real directory with it. The file store
# ignores hidden entries, so it is not a secret and never shows up in a listing.
: > "$SECRET_DEMO_DIR/.secret-demo-marker"

echo "Secret store: $SECRET_DEMO_DIR"
ls -l "$SECRET_DEMO_DIR"
echo
echo "camunda.secrets.missingToken is deliberately absent - scene 7 creates it live."
