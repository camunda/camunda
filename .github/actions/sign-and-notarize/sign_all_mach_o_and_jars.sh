#!/bin/bash
#
# sign_all_mach_o_and_jars.sh
#
# 1) Recursively sign all top-level Mach-O in <TARGET_DIR>, including .app bundles
#    with --deep at the .app root.
# 2) Find all .jar files, extract them, sign any Mach-O or .app inside,
#    then re-zip the jar.
#
# Usage:
#   ./sign_all_mach_o_and_jars.sh<TARGET_DIR> "<Developer ID Application: CertName (TEAMID)>"
#
# Requirements:
#   - 'file', 'codesign', 'unzip', 'zip' installed
#   - Developer ID Application cert in keychain
#   - Write permission in <TARGET_DIR>
#   - Enough disk space for jar extraction
#
# NOTE:
#   - If jars contain nested jars, you'd need a deeper approach.
#   - Java-level signatures in META-INF are invalidated, but Apple code signatures for
#     notarization are preserved.
#   - This script prevents re-signing Mach-O inside .app, which can break the .app signature.

set -e  # exit on error

if [ "$#" -ne 2 ]; then
  echo "Usage: $0 <TARGET_DIR> \"Developer ID Application: YourCert (TEAMID)\""
  exit 1
fi

TARGET_DIR="$1"
CERT_NAME="$2"

if [ ! -d "$TARGET_DIR" ]; then
  echo "Error: '$TARGET_DIR' is not a directory."
  exit 1
fi

#############################################################
# sign_macho_in_folder
# Recursively scans a folder for either:
#  - .app bundles => sign them once with --deep
#  - Mach-O files => sign individually
#
# The .app logic ensures we don't break the .app signature
# by re-signing its internal executable afterwards.
#############################################################
sign_macho_in_folder() {
  local folder="$1"
  local signed_count=0
  local failed_count=0
  local failed_files=()

  echo "  -> Scanning: $folder"

  # We'll examine both files & directories with 'find'.
  # Carefully skip deeper scanning if we sign an .app bundle root.
  while IFS= read -r -d '' candidate; do

    # If candidate is a directory that ends with .app, sign the entire bundle
    if [ -d "$candidate" ] && [[ "$candidate" == *.app ]]; then
      echo "    Found .app bundle: $candidate"
      echo "    Signing .app root with --deep..."
      if codesign --force --deep --options runtime --timestamp \
          --sign "$CERT_NAME" "$candidate" 2>/dev/null; then
        ((signed_count++))
      else
        echo "    [Error] Failed to sign bundle $candidate" >&2
        failed_files+=("$candidate")
        ((failed_count++))
      fi

      # Skip deeper scanning inside this .app, so continue
      # 'continue' only affects the while loop, skipping to next candidate
      continue
    fi

    # If candidate is a file, check if Mach-O
    if [ -f "$candidate" ]; then
      # We'll skip anything that's inside an .app, since we already signed it
      # But we need a reliable way to detect if candidate is inside .app
      # We'll do a quick check: if the path has '.app/' in it
      if [[ "$candidate" == *.app/* ]]; then
        # It's inside a .app we just signed, skip it
        continue
      fi

      # Otherwise, see if it's Mach-O
      if file -b "$candidate" | grep -q "Mach-O"; then
        echo "    Signing Mach-O file: $candidate"
        if codesign --force --options runtime --timestamp \
            --sign "$CERT_NAME" "$candidate" 2>/dev/null; then
          ((signed_count++))
        else
          echo "    [Error] Failed to sign $candidate" >&2
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
    # Optionally return 1 if you want to stop on failure
  fi
}

#########################################
# STEP 1: SIGN TOP-LEVEL MACH-O
#########################################
echo "=== STEP 1: Signing top-level code in '$TARGET_DIR' (incl. .app) ==="
sign_macho_in_folder "$TARGET_DIR"
echo

#########################################
# STEP 2: FIND .jar FILES, EXTRACT, SIGN, RE-ZIP
#########################################
echo "=== STEP 2: Searching for .jar files, signing Mach-O & .app inside them ==="
jar_count=0
processed_count=0

while IFS= read -r -d '' jar_file; do
  ((jar_count++))
  echo "[$jar_count] Processing JAR: $jar_file"

  # Convert jar_file to absolute path
  jar_abs_path="$(cd "$(dirname "$jar_file")" && pwd -P)/$(basename "$jar_file")"

  # 1) Temp folder
  tmpdir="$(mktemp -d)"
  # 2) Unzip jar
  jar -xf "$jar_abs_path" -C "$tmpdir"

  # 3) sign Mach-O or .app inside extracted folder
  sign_macho_in_folder "$tmpdir"

  # 4) Remove old jar
  rm -f "$jar_abs_path"

  # 5) Rebuild jar
  (
    cd "$tmpdir"
    echo "  -> Re-zipping into $jar_abs_path"
    jar -uf "$jar_abs_path" ./
  )

  rm -rf "$tmpdir"
  echo "  -> Done re-building $jar_abs_path"
  ((processed_count++))
  echo
done < <(find "$TARGET_DIR" -type f -name '*.jar' -print0)

echo "Scanned $jar_count .jar files total. Rebuilt $processed_count jars."
echo "=== Done! ==="
