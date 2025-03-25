#!/bin/bash
#
# exclude_sign_notarize_reinject_absolute.sh
#
# 1) Moves out top-level items in <C8RUN_DIR> whose names begin with:
#      - camunda-zeebe
#      - connector-runtime-bundle
#      - elasticsearch
#    so they are excluded from code signing.
# 2) Signs the rest of c8run (Mach-O, .app, .jar).
# 3) Builds c8run_signed.zip (without the excluded items) using ditto.
# 4) Notarizes c8run_signed.zip with xcrun notarytool submit.
# 5) If status is Accepted, unzips c8run_signed.zip, re-adds excluded items,
#    then re-zips as c8run_complete.zip.
#
# Usage:
#   ./exclude_sign_notarize_reinject_absolute.sh <C8RUN_DIR> \
#       "<Developer ID Application: Cert (TEAMID)>" <AppleIDEmail> <AppPassword> <TeamID>
#
# Example:
#   ./exclude_sign_notarize_reinject_absolute.sh ./c8run \
#       "Developer ID Application: Camunda Services GmbH (TEAMID)" \
#       "you@example.com" "abcd-wxyz" "TEAMID1234"
#
# NOTE: c8run_complete.zip includes un-signed items that were excluded. So
# if they contain Mach-O code, Gatekeeper may still block them. The notary
# service only scanned what was in c8run_signed.zip.

set -e  # exit on error

if [ "$#" -ne 5 ]; then
  echo "Usage: $0 <C8RUN_DIR> \"Developer ID Application: Cert (TEAMID)\" <AppleIDEmail> <AppPassword> <TeamID>"
  exit 1
fi

C8RUN_DIR="$1"
CERT_NAME="$2"
APPLE_ID="$3"
APPLE_PASS="$4"
APPLE_TEAM="$5"

# Ensure c8run exists
if [ ! -d "$C8RUN_DIR" ]; then
  echo "Error: '$C8RUN_DIR' is not a directory."
  exit 1
fi

##############################################
# Patterns to exclude at top-level:
##############################################
EXCLUDE_PREFIXES=("camunda-zeebe" "connector-runtime-bundle" "elasticsearch")

##############################################
# Temp dirs for exclude items + notarize steps
##############################################
TMP_EXCLUDE_DIR="$(mktemp -d)"
TMP_NOTARIZE_DIR="$(mktemp -d)"
echo "Created temp dirs: $TMP_EXCLUDE_DIR, $TMP_NOTARIZE_DIR"

##############################################
# A) Exclude subfolders/items from c8run
##############################################
echo "=== Step A: Excluding top-level items that begin with: ${EXCLUDE_PREFIXES[*]} ==="

find "$C8RUN_DIR" -maxdepth 1 \( -type f -o -type d \) -print0 | while IFS= read -r -d '' item; do
  base="$(basename "$item")"
  # Skip if it's c8run itself or hidden
  if [ "$base" = "$(basename "$C8RUN_DIR")" ] || [[ "$base" == .* ]]; then
    continue
  fi


  for prefix in "${EXCLUDE_PREFIXES[@]}"; do
    if [[ "$base" == "$prefix"* ]]; then

      # find the dylibs in the excluded folder and create a tmp fs for them
      for dylib in $( find $base -name "*.dylib" ); do
        zip -r "$TMP_EXCLUDE_DIR/$base-dylibs.zip" "$dylib"
      done

      echo "  -> Excluding $base -> $TMP_EXCLUDE_DIR"
      mv "$item" "$TMP_EXCLUDE_DIR"
      break
    fi
  done

  for dylibzip in $( find "$TMP_EXCLUDE_DIR" -name "*-dylibs.zip" ); do
    unzip $dylibzip
    rm $dylibzip
  done
done

##############################################
# B) Functions to sign Mach-O + jars
##############################################
sign_macho_in_folder() {
  local folder="$1"
  local signed_count=0

  echo "  -> Scanning for Mach-O/.app in: $folder"
  while IFS= read -r -d '' candidate; do
    echo "Candidate $candidate"

    # If .app
    if [ -d "$candidate" ] && [[ "$candidate" == *.app ]]; then
      echo "    Found .app: $candidate"
      if codesign --force --deep --options runtime --timestamp --sign "$CERT_NAME" "$candidate"; then
        ((signed_count++))
      else
        echo "[Error] .app sign failed: $candidate"
      fi
      continue
    fi

    # If Mach-O file
    if [ -f "$candidate" ]; then
      if [[ "$candidate" == *.app/* ]]; then
        continue  # skip inside .app
      fi
      if file -b "$candidate" | grep -q "Mach-O"; then
        echo "    Signing Mach-O: $candidate"
        if codesign --verbose=4 --force --options runtime --timestamp --sign "$CERT_NAME" "$candidate"; then
          ((signed_count++))
        else
          echo "[Error] Mach-O sign failed: $candidate"
        fi
      fi
    fi

  done < <(find "$folder" -print0)

  echo "  -> Signed $signed_count item(s) in $folder"
}

sign_jars_in_folder() {
  local folder="$1"
  local jar_count=0
  local jar_processed=0

  echo "  -> Searching for jars in: $folder"
  find "$folder" -type f -name '*.jar' -print0 | while IFS= read -r -d '' jar_file; do
    ((jar_count++))
    echo "    Processing jar: $jar_file"
    jar_abs="$(cd "$(dirname "$jar_file")" && pwd -P)/$(basename "$jar_file")"

    tmpjar="$(mktemp -d)"
    (cd "$tmpjar" && jar xvf "$jar_abs" >/dev/null)

    # sign Mach-O inside jar
    sign_macho_in_folder "$tmpjar"

    rm -f "$jar_abs"
    mkdir -p "$(dirname "$jar_abs")"

    (cd "$tmpjar" && jar cvf "$jar_abs" . >/dev/null)
    rm -rf "$tmpjar"

    ((jar_processed++))
    echo "    -> Rebuilt $jar_abs"
  done

  echo "  -> Found $jar_count jars, re-built $jar_processed"
}

##############################################
# C) Sign c8run
##############################################
echo "=== Step B: Signing leftover c8run content ==="
sign_macho_in_folder "$C8RUN_DIR"
sign_jars_in_folder "$C8RUN_DIR"

##############################################
# D) Create c8run_signed.zip with ditto
##############################################
echo "=== Step C: Creating c8run_signed.zip with ditto (excluding removed items) ==="
(
  cd "$(dirname "$C8RUN_DIR")"
  /usr/bin/ditto -c -k --keepParent "$(basename "$C8RUN_DIR")" c8run_signed.zip
)
SIGNED_ZIP_PATH="$(cd "$(dirname "$C8RUN_DIR")" && pwd -P)/c8run_signed.zip"
echo "Created: $SIGNED_ZIP_PATH"

##############################################
# E) Notarize c8run_signed.zip
##############################################
echo "=== Step D: Notarizing c8run_signed.zip ==="
xcrun notarytool submit "$SIGNED_ZIP_PATH" \
  --apple-id "$APPLE_ID" \
  --password "$APPLE_PASS" \
  --team-id "$APPLE_TEAM" \
  --wait
echo "Notarization succeeded (status: Accepted)"

##############################################
# F) Re-inject the excluded items & re-zip
##############################################
echo "=== Step E: Rebuilding final c8run_complete.zip with excluded items ==="
mkdir -p "$TMP_NOTARIZE_DIR/notarized"
# Unzip the notarized zip
unzip -q "$SIGNED_ZIP_PATH" -d "$TMP_NOTARIZE_DIR/notarized"

# Move excluded items back into c8run
echo "  -> Re-inserting excluded items into c8run"
find "$TMP_EXCLUDE_DIR" -mindepth 1 -maxdepth 1 -print0 | while IFS= read -r -d '' excluded; do
  base="$(basename "$excluded")"
  echo "    -> $base"
  mv -n "$excluded" "$TMP_NOTARIZE_DIR/notarized/c8run/"
done

# Build c8run_complete.zip
(
  cd "$TMP_NOTARIZE_DIR/notarized"
  /usr/bin/ditto -c -k --keepParent c8run c8run_complete.zip
)
FINAL_ZIP="$(dirname "$C8RUN_DIR")/c8run_complete.zip"
mv "$TMP_NOTARIZE_DIR/notarized/c8run_complete.zip" "$FINAL_ZIP"

echo "All done. Final archive with excluded items is: $FINAL_ZIP"

##############################################
# Cleanup
##############################################
rm -rf "$TMP_EXCLUDE_DIR" "$TMP_NOTARIZE_DIR"

cat <<EOM
Note: c8run_complete.zip includes the previously excluded items, which remain
unsigned/unnotarized if they contain Mach-O. Apple only notarized c8run_signed.zip.
EOM

