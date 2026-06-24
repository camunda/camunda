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
C8RUN_BASENAME="$(basename "$C8RUN_DIR")"

##############################################
# Patterns to exclude at top-level:
##############################################
EXCLUDE_PREFIXES=("camunda-zeebe" "connector-runtime-bundle" "elasticsearch")

##############################################
# Literal directory names that identify a bundled JVM runtime.
# Each entry is matched as an exact path component (not a glob), so "jre"
# matches .../jre/... but not .../jre-custom/... or .../myjre/...
# Any Mach-O binary under one of these directories is signed with
# JRE_ENTITLEMENTS (allow-jit + allow-unsigned-executable-memory +
# disable-library-validation) instead of the default hardened-runtime flags.
# Add a new entry here when bundling an additional JRE/JDK under a new name.
##############################################
JRE_DIR_NAMES=("jre" "jdk" "runtime")

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
  if [ "$base" = "$C8RUN_BASENAME" ] || [[ "$base" == .* ]]; then
    continue
  fi


  for prefix in "${EXCLUDE_PREFIXES[@]}"; do
    if [[ "$base" == "$prefix"* ]]; then

      # find the dylibs in the excluded folder and create a tmp fs for them
      for dylib in $( find $item -name "*.dylib" ); do
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
# JRE entitlements — required for JVM JIT on Apple Silicon.
# Without allow-jit the hardened runtime blocks pthread_jit_write_protect_np,
# which crashes libjvm.dylib during Threads::create_vm.
##############################################
JRE_ENTITLEMENTS="$(mktemp "${TMPDIR:-/tmp}/jre-entitlements.XXXXXX").plist"
cat > "$JRE_ENTITLEMENTS" << 'PLIST'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>com.apple.security.cs.allow-jit</key>
    <true/>
    <key>com.apple.security.cs.allow-unsigned-executable-memory</key>
    <true/>
    <key>com.apple.security.cs.disable-library-validation</key>
    <true/>
</dict>
</plist>
PLIST

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
        # Binaries inside a JVM runtime need JIT entitlements so the JVM can
        # call pthread_jit_write_protect_np under the hardened runtime on Apple Silicon.
        # The set of recognized runtime directory names is in JRE_DIR_NAMES.
        local entitlements_flag=()
        for jre_dir in "${JRE_DIR_NAMES[@]}"; do
          if [[ "$candidate" == */"$jre_dir"/* ]]; then
            entitlements_flag=(--entitlements "$JRE_ENTITLEMENTS")
            break
          fi
        done
        if codesign --verbose=4 --force --options runtime --timestamp \
            "${entitlements_flag[@]}" --sign "$CERT_NAME" "$candidate"; then
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
echo "=== Step B: Signing leftover ${C8RUN_BASENAME} content ==="
sign_macho_in_folder "$C8RUN_DIR"
sign_jars_in_folder "$C8RUN_DIR"

##############################################
# C.5) Verify all signatures before notarizing
#
# Three checks on everything in $C8RUN_DIR (excluded items are already moved
# out, so this only covers what was actually signed above):
#
#   1. Every .app bundle   → codesign --verify --deep --strict
#   2. Every Mach-O file outside .app and .jar → codesign --verify --strict
#      Note: Mach-O inside .jar files are signed and immediately repackaged;
#      codesign --verify cannot reach them post-repackaging — they are covered
#      by the exit-on-error in sign_macho_in_folder.
#   3. Every Mach-O under a JRE_DIR_NAMES directory → assert allow-jit
#      entitlement is present (missing it crashes libjvm.dylib on Apple Silicon
#      under the hardened runtime via pthread_jit_write_protect_np).
##############################################
echo "=== Step C.5: Verifying all Mach-O signatures ==="
verify_errors=0

# Check 1: .app bundles
while IFS= read -r -d '' app_bundle; do
  if codesign --verify --deep --strict "$app_bundle" 2>/dev/null; then
    echo "  -> signature OK (.app): $app_bundle"
  else
    echo "[Error] invalid/missing signature (.app): $app_bundle"
    verify_errors=$((verify_errors + 1))
  fi
done < <(find "$C8RUN_DIR" -name "*.app" -type d -print0)

# Check 2 + 3: individual Mach-O files
while IFS= read -r -d '' candidate; do
  # skip files inside .app bundles (covered by --deep above)
  [[ "$candidate" == *.app/* ]] && continue
  file -b "$candidate" | grep -q "Mach-O" || continue

  # 2. Valid signature
  if codesign --verify --strict "$candidate" 2>/dev/null; then
    echo "  -> signature OK: $candidate"
  else
    echo "[Error] invalid/missing signature: $candidate"
    verify_errors=$((verify_errors + 1))
  fi

  # 3. JRE path → must carry all three JVM entitlements
  for jre_dir in "${JRE_DIR_NAMES[@]}"; do
    if [[ "$candidate" == */"$jre_dir"/* ]]; then
      entitlements_out="$(codesign -d --entitlements - "$candidate" 2>/dev/null)"
      for required_entitlement in \
          "com.apple.security.cs.allow-jit" \
          "com.apple.security.cs.allow-unsigned-executable-memory" \
          "com.apple.security.cs.disable-library-validation"; do
        if echo "$entitlements_out" | grep -q "$required_entitlement"; then
          echo "  -> $required_entitlement OK: $candidate"
        else
          echo "[Error] missing $required_entitlement on JVM runtime binary: $candidate"
          echo "        Check JRE_DIR_NAMES includes '$jre_dir' and sign_macho_in_folder"
          echo "        applies JRE_ENTITLEMENTS to that directory."
          verify_errors=$((verify_errors + 1))
        fi
      done
      break
    fi
  done
done < <(find "$C8RUN_DIR" -type f -print0)

if [ "$verify_errors" -gt 0 ]; then
  echo "[Error] $verify_errors verification failure(s). Aborting before notarization."
  exit 1
fi
echo "  -> All signed binaries verified."

##############################################
# D) Create c8run_signed.zip with ditto
##############################################
echo "=== Step C: Creating c8run_signed.zip with ditto (excluding removed items) ==="
(
  cd "$(dirname "$C8RUN_DIR")"
  /usr/bin/ditto -c -k --keepParent "$C8RUN_BASENAME" c8run_signed.zip
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
echo "  -> Re-inserting excluded items into ${C8RUN_BASENAME}"
find "$TMP_EXCLUDE_DIR" -mindepth 1 -maxdepth 1 -print0 | while IFS= read -r -d '' excluded; do
  base="$(basename "$excluded")"
  echo "    -> $base"
  rsync --ignore-existing -ar "$excluded" "$TMP_NOTARIZE_DIR/notarized/$C8RUN_BASENAME/"
done

# Build c8run_complete.zip
(
  cd "$TMP_NOTARIZE_DIR/notarized"
  /usr/bin/ditto -c -k --keepParent "$C8RUN_BASENAME" c8run_complete.zip
)
FINAL_ZIP="$(dirname "$C8RUN_DIR")/c8run_complete.zip"
mv "$TMP_NOTARIZE_DIR/notarized/c8run_complete.zip" "$FINAL_ZIP"

echo "All done. Final archive with excluded items is: $FINAL_ZIP"

##############################################
# G) JVM smoke test — verify the signed JRE actually starts
# Finds any java binary under JRE_DIR_NAMES in the notarized tree and
# runs -version. Catches signing regressions that only manifest at JVM init
# time (e.g. a newly added binary that was missed by the entitlements loop).
# Runs against the notarized, re-injected tree before any further packaging.
##############################################
echo "=== Step G: JVM smoke test ==="
NOTARIZED_ROOT="$TMP_NOTARIZE_DIR/notarized/$C8RUN_BASENAME"
smoke_tested=0
for jre_dir in "${JRE_DIR_NAMES[@]}"; do
  while IFS= read -r -d '' java_bin; do
    echo "  -> Running: $java_bin -version"
    if "$java_bin" -version; then
      echo "  -> JVM smoke test passed: $java_bin"
      smoke_tested=$((smoke_tested + 1))
    else
      echo "[Error] JVM smoke test FAILED: $java_bin"
      exit 1
    fi
  done < <(find "$NOTARIZED_ROOT" -path "*/${jre_dir}/bin/java" \( -type f -o -type l \) -print0)
done
if [ "$smoke_tested" -eq 0 ]; then
  echo "  -> No java binary found under JRE_DIR_NAMES in $NOTARIZED_ROOT; skipping smoke test."
fi

##############################################
# Cleanup
##############################################
rm -rf "$TMP_EXCLUDE_DIR" "$TMP_NOTARIZE_DIR" "$JRE_ENTITLEMENTS"

cat <<EOM
Note: c8run_complete.zip includes the previously excluded items, which remain
unsigned/unnotarized if they contain Mach-O. Apple only notarized c8run_signed.zip.
EOM
