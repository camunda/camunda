#!/bin/bash
#
# exclude_and_sign_all_in_one.sh
#
# 1) Moves out top-level items in <C8RUN_DIR> whose names begin with:
#      - camunda-zeebe
#      - connector-runtime-bundle
#      - elasticsearch
#    so they are not touched by signing steps.
# 2) Signs the rest of <C8RUN_DIR>:
#    - Recursively sign Mach-O code (including .app with --deep)
#    - For each JAR in the folder, extract and sign any Mach-O inside, then re-jar
# 3) Moves the excluded items back.
# 4) Uses ditto -c -k --keepParent to produce c8run_signed.zip.
#
# Usage:
#   ./exclude_and_sign_all_in_one.sh <C8RUN_DIR> \
#       "Developer ID Application: YourCert (TEAMID)"
#
# Example:
#   ./exclude_and_sign_all_in_one.sh ./c8run \
#       "Developer ID Application: Camunda Services GmbH (TEAMID)"
#
# Notes:
#   - If the excluded items contain Mach-O code, Apple’s notary may reject them
#     if they remain in the final .zip. If that’s acceptable, fine. Otherwise
#     you do need to sign them or move them out permanently.
#   - This script is for top-level excluding only. If they appear in subfolders,
#     you need to adjust or remove -maxdepth 1 usage.

set -e # exit on error

if [ "$#" -ne 2 ]; then
        echo "Usage: $0 <C8RUN_DIR> \"Developer ID Application: YourCert (TEAMID)\""
        exit 1
fi

C8RUN_DIR="$1"
CERT_NAME="$2"

# Validate main dir
if [ ! -d "$C8RUN_DIR" ]; then
        echo "Error: '$C8RUN_DIR' is not a directory."
        exit 1
fi

##############################
# Patterns to exclude at top-level
##############################
EXCLUDE_PREFIXES=("camunda-zeebe" "connector-runtime-bundle" "elasticsearch")

##############################
# Step 0: Temporary exclude folder
##############################
TMP_EXCLUDE_DIR="$(mktemp -d)"
echo "Created temp exclude dir: $TMP_EXCLUDE_DIR"

###########################################
# Step A: Move out top-level excluded items
###########################################
echo "Moving out top-level items whose names start with: ${EXCLUDE_PREFIXES[*]}"

# We'll do a top-level listing in c8run, ignoring hidden items
find "$C8RUN_DIR" -maxdepth 1 \( -type f -o -type d \) -print0 | while IFS= read -r -d '' item; do
        base="$(basename "$item")"
        # Skip if it's the c8run dir itself or hidden . files
        if [ "$base" = "$(basename "$C8RUN_DIR")" ] || [[ "$base" == .* ]]; then
                continue
        fi

        # Compare base name to each exclude prefix
        for prefix in "${EXCLUDE_PREFIXES[@]}"; do
                if [[ "$base" == "$prefix"* ]]; then
                        echo "  -> Excluding $base (moving to $TMP_EXCLUDE_DIR)"
                        mv "$item" "$TMP_EXCLUDE_DIR"
                        break
                fi
        done
done

#############################
# Step B: sign the remainder
#############################
echo "Signing c8run with Mach-O + .app + jar approach"

#  B1) sign_macho_in_folder (function)
sign_macho_in_folder() {
        local folder="$1"
        local signed_count=0
        local failed_count=0
        local failed_files=()

        echo "  -> Scanning: $folder"

        # We'll do a simple find, ignoring .app internals after signing the .app
        while IFS= read -r -d '' candidate; do

                # If candidate is .app
                if [ -d "$candidate" ] && [[ "$candidate" == *.app ]]; then
                        echo "    Found .app: $candidate"
                        if codesign --force --deep --options runtime --timestamp \
                                --sign "$CERT_NAME" "$candidate" 2>/dev/null; then
                                ((signed_count++))
                        else
                                echo "[Error] Failed to sign bundle $candidate" >&2
                                failed_files+=("$candidate")
                                ((failed_count++))
                        fi
                        # skip deeper scanning
                        continue
                fi

                # If file is Mach-O
                if [ -f "$candidate" ]; then
                        if [[ "$candidate" == *.app/* ]]; then
                                # skip inside .app
                                continue
                        fi
                        if file -b "$candidate" | grep -q "Mach-O"; then
                                echo "    Signing Mach-O file: $candidate"
                                if codesign --force --options runtime --timestamp \
                                        --sign "$CERT_NAME" "$candidate" 2>/dev/null; then
                                        ((signed_count++))
                                else
                                        echo "[Error] Failed to sign $candidate" >&2
                                        failed_files+=("$candidate")
                                        ((failed_count++))
                                fi
                        fi
                fi

        done < <(find "$folder" -print0)

        echo "  -> Signed $signed_count item(s) in $folder"
        if [ "$failed_count" -gt 0 ]; then
                echo "  -> Failed to sign $failed_count item(s):"
                for f in "${failed_files[@]}"; do
                        echo "       $f"
                done
        fi
}

#  B2) sign_jars_in_folder (extract -> sign -> re-zip)
sign_jars_in_folder() {
        local folder="$1"
        local jar_count=0
        local jar_processed=0

        echo "  -> Searching for .jar files in $folder"

        # We'll do a simple find for .jar
        find "$folder" -type f -name '*.jar' -print0 | while IFS= read -r -d '' jar_file; do
                ((jar_count++))
                echo "    Processing JAR: $jar_file"

                jar_abs="$(cd "$(dirname "$jar_file")" && pwd -P)/$(basename "$jar_file")"
                tmpjar="$(mktemp -d)"

                (
                        cd "$tmpjar"
                        jar xvf "$jar_abs" >/dev/null 2>&1
                )

                # sign Mach-O inside the extracted jar
                sign_macho_in_folder "$tmpjar"

                # remove old jar
                rm -f "$jar_abs"

                # re-jar
                mkdir -p "$(dirname "$jar_abs")"
                (
                        cd "$tmpjar"
                        jar cvf "$jar_abs" . >/dev/null 2>&1
                )

                rm -rf "$tmpjar"
                ((jar_processed++))
                echo "    -> Done re-building $jar_abs"
        done

        echo "  -> Found $jar_count jars, re-built $jar_processed"
}

##  B3) Actually sign c8run
echo "=== Signing top-level Mach-O ==="
sign_macho_in_folder "$C8RUN_DIR"

echo "=== Signing JARs (extract -> sign -> re-jar) ==="
sign_jars_in_folder "$C8RUN_DIR"

#############################
# Step C: Move them back
#############################
echo "Moving excluded items back"
find "$TMP_EXCLUDE_DIR" -mindepth 1 -maxdepth 1 -print0 | while IFS= read -r -d '' item; do
        base="$(basename "$item")"
        echo "  -> returning $base to c8run"
        mv "$item" "$C8RUN_DIR"
done

############################
# Step D: ditto => c8run_signed.zip
############################
echo "Creating c8run_signed.zip with ditto..."
cd "$(dirname "$C8RUN_DIR")"
/usr/bin/ditto -c -k --keepParent "$(basename "$C8RUN_DIR")" c8run_signed.zip
echo "All done: c8run_signed.zip"

rm -rf "$TMP_EXCLUDE_DIR"
echo "Finished. Excluded top-level camunda-zeebe*, connector-runtime-bundle*, elasticsearch* from signing."
