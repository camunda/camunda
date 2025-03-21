#!/bin/bash
#
# exclude_sign_notarize_reinject.sh
#
# 1) Moves out top-level items in c8run whose names start with:
#      - camunda-zeebe
#      - connector-runtime-bundle
#      - elasticsearch
#    so they are excluded from code signing.
# 2) Signs the rest of c8run:
#    - Mach-O files, .app bundles, .jar extraction & re-sign
# 3) Creates c8run_signed.zip (without excluded items) using ditto.
# 4) Calls 'xcrun notarytool submit c8run_signed.zip --wait' for notarization.
# 5) If status is Accepted, re-inject the excluded items into the final .zip.
#
# Usage:
#   ./exclude_sign_notarize_reinject.sh <C8RUN_DIR> "<Developer ID Application: MyCert (TEAMID)>" \
#       <AppleIDEmail> <AppSpecificPassword> <TeamID>
#
# Example:
#   ./exclude_sign_notarize_reinject.sh ./c8run "Developer ID Application: Camunda Services (TEAMID)" \
#       "you@apple.com" "abcd-efgh-ijkl" "TEAMID1234"
#
# NOTES:
#   - The final c8run_complete.zip is no longer the exact file Apple saw. Any newly added Mach-O remains unsigned.
#   - This script relies on 'jar' for .jar extraction/rebuilding or 'unzip/zip' if you prefer. Shown below is a jar approach.
#   - If you have more patterns to exclude, add them to EXCLUDE_PREFIXES.

set -e # exit on error

if [ "$#" -ne 5 ]; then
        echo "Usage: $0 <C8RUN_DIR> \"Developer ID Application: Cert (TEAMID)\" <AppleIDEmail> <AppSpecificPassword> <TeamID>"
        exit 1
fi

C8RUN_DIR="$1"
CERT_NAME="$2"
APPLE_ID="$3"
APPLE_PASS="$4"
APPLE_TEAM="$5"

if [ ! -d "$C8RUN_DIR" ]; then
        echo "Error: '$C8RUN_DIR' is not a directory."
        exit 1
fi

###########################################
# 0) Patterns to exclude at top-level
###########################################
EXCLUDE_PREFIXES=("camunda-zeebe" "connector-runtime-bundle" "elasticsearch")

###########################################
# Temporary directories
###########################################
TMP_EXCLUDE_DIR="$(mktemp -d)"
TMP_NOTARIZE_DIR="$(mktemp -d)"

echo "Created temp dirs: $TMP_EXCLUDE_DIR, $TMP_NOTARIZE_DIR"

###########################################
# A) Move out top-level excluded items
###########################################
echo "=== Step A: Excluding top-level items named ${EXCLUDE_PREFIXES[*]}* from c8run ==="
find "$C8RUN_DIR" -maxdepth 1 \( -type f -o -type d \) -print0 | while IFS= read -r -d '' item; do
        base="$(basename "$item")"
        # Skip c8run folder itself or hidden items
        if [ "$base" = "$(basename "$C8RUN_DIR")" ] || [[ "$base" == .* ]]; then
                continue
        fi

        for prefix in "${EXCLUDE_PREFIXES[@]}"; do
                if [[ "$base" == "$prefix"* ]]; then
                        echo "  -> Excluding $base -> $TMP_EXCLUDE_DIR"
                        mv "$item" "$TMP_EXCLUDE_DIR"
                        break
                fi
        done
done

###########################################
# B) sign_macho_in_folder: sign .app & Mach-O
###########################################
sign_macho_in_folder() {
        local folder="$1"
        local signed_count=0

        echo "  -> Scanning for Mach-O/.app in: $folder"
        while IFS= read -r -d '' candidate; do

                if [ -d "$candidate" ] && [[ "$candidate" == *.app ]]; then
                        echo "    Found .app: $candidate"
                        if codesign --force --deep --options runtime --timestamp --sign "$CERT_NAME" "$candidate"; then
                                ((signed_count++))
                        else
                                echo "    [Error] .app sign failed: $candidate"
                        fi
                        continue
                fi

                if [ -f "$candidate" ]; then
                        if [[ "$candidate" == *.app/* ]]; then
                                continue # skip inside .app
                        fi

                        # Check Mach-O
                        if file -b "$candidate" | grep -q "Mach-O"; then
                                echo "    Signing Mach-O: $candidate"
                                if codesign --force --options runtime --timestamp --sign "$CERT_NAME" "$candidate"; then
                                        ((signed_count++))
                                else
                                        echo "    [Error] Mach-O sign failed: $candidate"
                                fi
                        fi
                fi

        done < <(find "$folder" -print0)

        echo "  -> Signed $signed_count item(s) in $folder"
}

###########################################
# B) sign_jars_in_folder: for each .jar, extract -> sign -> re-jar
###########################################
sign_jars_in_folder() {
        local folder="$1"
        local jar_count=0
        local jar_processed=0

        echo "  -> Searching for .jar in: $folder"
        find "$folder" -type f -name '*.jar' -print0 | while IFS= read -r -d '' jar_file; do
                ((jar_count++))
                echo "    Processing jar: $jar_file"
                jar_abs="$(cd "$(dirname "$jar_file")" && pwd -P)/$(basename "$jar_file")"

                tmpjar="$(mktemp -d)"
                (cd "$tmpjar" && jar xvf "$jar_abs" >/dev/null)

                # Now sign Mach-O inside that extracted jar
                sign_macho_in_folder "$tmpjar"

                # remove old jar
                rm -f "$jar_abs"
                mkdir -p "$(dirname "$jar_abs")"

                # rebuild
                (cd "$tmpjar" && jar cvf "$jar_abs" . >/dev/null)
                rm -rf "$tmpjar"

                ((jar_processed++))
                echo "    -> Rebuilt $jar_abs"
        done

        echo "  -> Found $jar_count jars, processed $jar_processed"
}

###########################################
# C) Sign c8run
###########################################
echo "=== Step B: Signing remaining content in c8run ==="
# 1) sign top-level Mach-O & .app
sign_macho_in_folder "$C8RUN_DIR"
# 2) sign jars
sign_jars_in_folder "$C8RUN_DIR"

###########################################
# D) Build c8run_signed.zip with ditto
###########################################
echo "=== Step C: Creating c8run_signed.zip (excluding removed items) ==="
(
        cd "$(dirname "$C8RUN_DIR")"
        /usr/bin/ditto -c -k --keepParent "$(basename "$C8RUN_DIR")" c8run_signed.zip
)
SIGNED_ZIP_PATH="$(dirname "$C8RUN_DIR")/c8run_signed.zip"

###########################################
# E) Notarize c8run_signed.zip
###########################################
echo "=== Step D: Submitting c8run_signed.zip for notarization ==="
xcrun notarytool submit "$SIGNED_ZIP_PATH" \
        --apple-id "$APPLE_ID" \
        --password "$APPLE_PASS" \
        --team-id "$APPLE_TEAM" \
        --wait

# If we get here, no error => status: Accepted
echo "Notarization successful (status: Accepted)"

###########################################
# F) Re-inject excluded items into final .zip
###########################################
echo "=== Step E: Rebuilding final c8run_with_extras.zip by re-adding excluded items ==="
# 1) Unzip the notarized zip into a temp folder
rm -rf "$TMP_NOTARIZE_DIR"
mkdir -p "$TMP_NOTARIZE_DIR/notarized"
(
        cd "$TMP_NOTARIZE_DIR/notarized"
        unzip -q "$SIGNED_ZIP_PATH"
)
# The c8run folder is now in $TMP_NOTARIZE_DIR/notarized/c8run

# 2) Move the excluded items back into that c8run
echo "  -> Re-inserting excluded items..."
while IFS= read -r -d '' excluded; do
        base="$(basename "$excluded")"
        echo "    -> $base"
        mv "$excluded" "$TMP_NOTARIZE_DIR/notarized/c8run/"
done < <(find "$TMP_EXCLUDE_DIR" -mindepth 1 -maxdepth 1 -print0)

# 3) Build c8run_complete.zip
(
        cd "$TMP_NOTARIZE_DIR/notarized"
        /usr/bin/ditto -c -k --keepParent "c8run" c8run_complete.zip
)
mv "$TMP_NOTARIZE_DIR/notarized/c8run_complete.zip" "$(dirname "$C8RUN_DIR")"

echo "All done. The final .zip is c8run_complete.zip in $(dirname "$C8RUN_DIR")"

###########################################
# Cleanup
###########################################
rm -rf "$TMP_EXCLUDE_DIR" "$TMP_NOTARIZE_DIR"

cat <<EOM
Note: c8run_complete.zip now includes the previously excluded items,
but that final archive is no longer the exact artifact Apple notarized.
Any Mach-O inside the re-injected subfolders remains unsigned.
EOM
