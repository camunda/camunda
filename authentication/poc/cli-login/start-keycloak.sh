#!/usr/bin/env bash
# Downloads Keycloak (once) and starts it in dev mode on :18080 with the PoC realms imported.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
KEYCLOAK_VERSION="${KEYCLOAK_VERSION:-26.7.4}"
KEYCLOAK_DIR="${KEYCLOAK_DIR:-$HERE/.keycloak}"
KEYCLOAK_HOME="$KEYCLOAK_DIR/keycloak-$KEYCLOAK_VERSION"

if [ ! -x "$KEYCLOAK_HOME/bin/kc.sh" ]; then
  mkdir -p "$KEYCLOAK_DIR"
  echo "Downloading Keycloak $KEYCLOAK_VERSION ..."
  curl -fsSL -o "$KEYCLOAK_DIR/keycloak.tar.gz" \
    "https://github.com/keycloak/keycloak/releases/download/$KEYCLOAK_VERSION/keycloak-$KEYCLOAK_VERSION.tar.gz"
  tar -xzf "$KEYCLOAK_DIR/keycloak.tar.gz" -C "$KEYCLOAK_DIR"
  rm "$KEYCLOAK_DIR/keycloak.tar.gz"
fi

# Start from a fresh dev database so the realm file is always the source of truth.
rm -rf "$KEYCLOAK_HOME/data/h2"
mkdir -p "$KEYCLOAK_HOME/data/import"
rm -f "$KEYCLOAK_HOME"/data/import/*.json
cp "$HERE"/keycloak/*-realm.json "$KEYCLOAK_HOME/data/import/"

export KC_BOOTSTRAP_ADMIN_USERNAME="${KC_BOOTSTRAP_ADMIN_USERNAME:-admin}"
export KC_BOOTSTRAP_ADMIN_PASSWORD="${KC_BOOTSTRAP_ADMIN_PASSWORD:-admin}"

exec "$KEYCLOAK_HOME/bin/kc.sh" start-dev --http-port=18080 --import-realm
