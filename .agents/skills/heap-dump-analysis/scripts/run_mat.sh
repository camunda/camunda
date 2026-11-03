#!/usr/bin/env bash
# Run Eclipse MAT headless on one heap dump. See ../references/mat-headless.md.
# Usage: OUT_DIR=<dir outside any repo> run_mat.sh <dump.hprof> [pack.xml ...]
# Env: NAME (output name), MAT_HOME, MAT_XMX (default 12g). Default pack: the starter pack.
# Output goes to $OUT_DIR/<name>/raw/ and holds heap values: never read or print it.
set -euo pipefail
umask 077

: "${OUT_DIR:?Set OUT_DIR to a dir outside any repo. The output holds customer data.}"
dump=${1:?usage: OUT_DIR=<dir> run_mat.sh <dump.hprof> [pack.xml ...]}
shift
dir=$(cd -- "$(dirname -- "$dump")" 2>/dev/null && pwd) || { echo "No dump at $dump" >&2; exit 2; }
file=$(basename -- "$dump")
[ -f "$dir/$file" ] || { echo "No dump at $dump" >&2; exit 2; }
# Looks for .git itself: asking git fails open when git is missing or refuses the dir.
refuse_repo() {
  local d=$1
  while [ ! -d "$d" ]; do d=$(dirname -- "$d"); done
  d=$(cd -- "$d" && pwd -P)
  while :; do
    if [ -e "$d/.git" ]; then
      echo "$1 is inside the git repo $d. Keep dumps and output out of repos." >&2; exit 2
    fi
    [ "$d" != / ] || return 0
    d=$(dirname -- "$d")
  done
}
refuse_repo "$OUT_DIR"
refuse_repo "$dir"
[ $# -gt 0 ] || set -- "$(cd -- "$(dirname -- "$0")" >/dev/null && pwd)/../queries/starter_pack.xml"
MAT_HOME=${MAT_HOME:-/Applications/MemoryAnalyzer.app/Contents/Eclipse}
jars=("$MAT_HOME"/plugins/org.eclipse.equinox.launcher_*.jar)
launcher=${jars[${#jars[@]} - 1]}
[ -f "$launcher" ] || {
  echo "No launcher jar in $MAT_HOME/plugins. Set MAT_HOME to the MAT dir that has plugins/." >&2; exit 2
}

# Broker dumps are often all named java_pid1.hprof.
name=$(printf '%s' "${file%.hprof}" | sed -E 's/_?java_pid[0-9]+$//')
name=${NAME:-${name:-$(basename -- "$dir")_${file%.hprof}}}
raw=$OUT_DIR/$name/raw
mkdir -p "$raw/reports"
# NAME or a symlink can lead out of OUT_DIR.
refuse_repo "$raw"
if [ -f "$raw/.dump" ] && [ "$(cat "$raw/.dump")" != "$dir/$file" ]; then
  echo "$OUT_DIR/$name belongs to $(cat "$raw/.dump"). Set NAME." >&2; exit 2
fi
printf '%s\n' "$dir/$file" >"$raw/.dump"
# MAT names its index files and report zips after the dump without its last extension.
prefix=$dir/${file%.*}

failed=0
for pack in "$@"; do
  # MAT turns other characters into _ in the zip name, so allow only these.
  sfx=$(sed -n 's/.*key="filename_suffix" value="\([A-Za-z0-9_]*\)".*/\1/p' "$pack")
  [ -n "$sfx" ] || { echo "$pack: needs a filename_suffix of letters, digits and _ only" >&2; exit 2; }
  zip=${prefix}_${sfx:0:20}.zip
  log=$raw/mat_$sfx.log
  first=""
  [ -f "$prefix.index" ] || first=1
  if [ -n "$first" ] && [ "${sfx#raw_}" != "$sfx" ]; then
    echo "$pack: a raw_ pack can't be the first parse of a dump. Run the starter pack first." >&2; exit 2
  fi
  rm -rf "${raw:?}/reports/$sfx" "$raw/$sfx.zip" "$zip"
  mkdir -p "$raw/reports/$sfx"
  t0=$SECONDS
  rc=0
  # The launcher jar: the native launcher opens a GUI dialog on errors and hangs.
  java -Xmx"${MAT_XMX:-12g}" -DhprofStrictnessPermissive=true -Duser.language=en -Duser.country=US \
    -jar "$launcher" -consolelog -nosplash -data "$raw/.ws" \
    -application org.eclipse.mat.api.parse "$dir/$file" "$pack" >"$log" 2>&1 || rc=$?
  # Only the first parse logs truncation. Reruns reuse the indexes.
  if [ -n "$first" ]; then cp "$log" "$raw/first_parse.log"; fi
  truncated=unknown
  if [ -f "$raw/first_parse.log" ]; then truncated=$(grep -c 'Expected to read' "$raw/first_parse.log" || true); fi
  bad=$rc note=""
  if [ -f "$zip" ]; then
    mv -f "$zip" "$raw/$sfx.zip"
    urc=0
    unzip -q -o -j "$raw/$sfx.zip" '*.csv' -d "$raw/reports/$sfx" >>"$log" 2>&1 || urc=$?
    # 11: no CSV at all, because every query had zero rows or failed.
    if [ "$urc" != 0 ] && [ "$urc" != 11 ]; then note=", unzip failed (rc=$urc)"; bad=1; fi
    # A broken query gives rc 0 and no CSV. Only the report HTML says "Problem reported".
    problems=$(unzip -p "$raw/$sfx.zip" '*.html' 2>/dev/null | grep -c 'Problem reported' || true)
    note="$note, problems=${problems:-0}"
    if [ "${problems:-0}" != 0 ]; then bad=1; fi
  else note=", no report zip"; bad=1; fi
  echo "$sfx: rc=$rc $((SECONDS - t0))s$note, truncated=$truncated, CSVs in $raw/reports/$sfx:"
  ls "$raw/reports/$sfx"
  if [ "$bad" != 0 ]; then failed=1; fi
done
exit "$failed"
