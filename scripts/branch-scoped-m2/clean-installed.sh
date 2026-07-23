#!/usr/bin/env bash
#
# Clean up the branch-scoped Maven local-repository partition
# (~/.m2/repository/installed) populated by the branch-scoped-local-repository
# Maven extension (see .mvn/extensions.xml and this directory's README.md).
#
# The extension installs artifacts under:
#     ~/.m2/repository/installed/<branch>/<groupId>/<artifactId>/<version>/...
# where <branch> is the checked-out git branch (slashes preserved, so
# `ls/my-feature` and `backport-123-to-stable/8.7` are nested), or
# `detached-<short-sha>` for a detached HEAD.
#
# Two cleanup modes are offered:
#   --stale-branches   remove trees whose <branch> is no longer a local git branch
#   --older-than[=N]   remove trees untouched for more than N days (default 7)
#
# Dry-run by default; pass --delete to actually remove anything.
#
# Requires: git, GNU or BSD `find` with -newermt (both supported).
set -euo pipefail

# --- configuration --------------------------------------------------------
# Maven groupId first-segments. Used to detect where the <branch> path ends and
# the Maven coordinate begins (the first matching segment is the coordinate
# root; everything before it is the branch). This monorepo only installs
# io.camunda / io.github.*, but the set is kept small-and-general and can be
# extended if a module ever installs under a different groupId.
GROUP_ROOTS=(io com org)

INSTALLED="${HOME}/.m2/repository/installed"
REPO=""
MODE=""
DAYS=7
DO_DELETE=false

# --- helpers --------------------------------------------------------------
usage() {
  cat <<'EOF'
Usage: clean-installed.sh (--stale-branches | --older-than[=DAYS]) [options]

Modes (exactly one required):
  --stale-branches      Remove installed trees whose branch no longer exists
                        as a local git branch (also matches detached-*/worktree-*
                        entries, which never correspond to a live branch).
  --older-than[=DAYS]   Remove installed trees with no file modified in the last
                        DAYS days (default: 7). Applies regardless of branch.

Options:
  --delete, -f          Actually delete. Without this the script only prints what
                        it would remove (dry-run).
  --installed PATH      Path to the 'installed' partition
                        (default: ~/.m2/repository/installed).
  --repo PATH           Git checkout used to resolve live branches for
                        --stale-branches (default: the repo containing this script).
  -h, --help            Show this help.

Examples:
  clean-installed.sh --stale-branches            # preview dead-branch trees
  clean-installed.sh --stale-branches --delete   # remove them
  clean-installed.sh --older-than=14 --delete    # remove trees idle >2 weeks
EOF
}

die() { echo "error: $*" >&2; exit 2; }

# --- argument parsing -----------------------------------------------------
while [[ $# -gt 0 ]]; do
  case "$1" in
    --stale-branches) MODE=stale; shift ;;
    --older-than)
      MODE=age; shift
      if [[ "${1:-}" =~ ^[0-9]+$ ]]; then DAYS="$1"; shift; fi
      ;;
    --older-than=*) MODE=age; DAYS="${1#*=}"; shift ;;
    --delete|-f|--force) DO_DELETE=true; shift ;;
    --installed) INSTALLED="${2:?--installed needs a path}"; shift 2 ;;
    --installed=*) INSTALLED="${1#*=}"; shift ;;
    --repo) REPO="${2:?--repo needs a path}"; shift 2 ;;
    --repo=*) REPO="${1#*=}"; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "unknown argument: $1" >&2; usage; exit 2 ;;
  esac
done

[[ -n "$MODE" ]] || { usage; die "choose --stale-branches or --older-than[=DAYS]"; }
[[ "$DAYS" =~ ^[0-9]+$ ]] || die "DAYS must be a non-negative integer (got '$DAYS')"

# Seatbelt: refuse to operate on anything that isn't an 'installed' partition,
# so a mistyped --installed can't turn into rm -rf on an unrelated tree.
[[ "$(basename "$INSTALLED")" == "installed" ]] \
  || die "--installed must point at the '.../installed' partition (got '$INSTALLED')"

if [[ ! -d "$INSTALLED" ]]; then
  echo "Nothing to clean: $INSTALLED does not exist."
  exit 0
fi

# --- resolve live branches (stale mode) -----------------------------------
declare -A LIVE=()
if [[ "$MODE" == stale ]]; then
  if [[ -z "$REPO" ]]; then
    script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    REPO="$(git -C "$script_dir" rev-parse --show-toplevel 2>/dev/null || true)"
  fi
  [[ -n "$REPO" ]] && git -C "$REPO" rev-parse --git-dir >/dev/null 2>&1 \
    || die "--stale-branches needs a git repo; pass --repo <path> or run inside the checkout"
  while IFS= read -r b; do LIVE["$b"]=1; done \
    < <(git -C "$REPO" for-each-ref --format='%(refname:short)' refs/heads)
fi

# --- compute the age cutoff (age mode) ------------------------------------
CUTOFF=""
if [[ "$MODE" == age ]]; then
  if date -d "@0" >/dev/null 2>&1; then
    CUTOFF="$(date -d "-${DAYS} days" '+%Y-%m-%d %H:%M:%S')"   # GNU date
  else
    CUTOFF="$(date -v-"${DAYS}"d '+%Y-%m-%d %H:%M:%S')"        # BSD/macOS date
  fi
fi

# --- enumerate install trees ("units") ------------------------------------
# Find the shallowest groupId-root dir on each path; the segment(s) before it
# are the branch. -prune stops descent so we get one hit per coordinate root.
find_expr=()
for g in "${GROUP_ROOTS[@]}"; do
  [[ ${#find_expr[@]} -gt 0 ]] && find_expr+=( -o )
  find_expr+=( -name "$g" )
done

mapfile -t group_dirs < <(
  find "$INSTALLED" -type d \( "${find_expr[@]}" \) -prune -print 2>/dev/null | sort
)

declare -A seen=()
declare -A unit_flat=()    # unit path -> 1 if a flat (non-branch-scoped) install
declare -A unit_branch=()  # unit path -> branch label
units=()
flat_count=0
for g in "${group_dirs[@]}"; do
  rel="${g#"$INSTALLED"/}"
  branch="$(dirname "$rel")"
  if [[ "$branch" == "." ]]; then
    path="$g"; flat=1          # legacy flat install: unit is the groupId dir itself
  else
    path="$INSTALLED/$branch"; flat=0
  fi
  if [[ -z "${seen[$path]:-}" ]]; then
    seen["$path"]=1
    units+=("$path")
    unit_flat["$path"]=$flat
    unit_branch["$path"]="$branch"
    [[ $flat -eq 1 ]] && flat_count=$((flat_count + 1))
  fi
done

# --- header ---------------------------------------------------------------
echo "Branch-scoped Maven repository cleanup"
echo "  partition: $INSTALLED"
if [[ "$MODE" == stale ]]; then
  echo "  mode:      stale-branches (branches from: $REPO)"
else
  echo "  mode:      older-than ${DAYS}d (cutoff: $CUTOFF)"
fi
if $DO_DELETE; then echo "  action:    DELETE"; else echo "  action:    dry-run (pass --delete to remove)"; fi
echo

# --- select units to remove -----------------------------------------------
to_remove=()
reasons=()
for path in "${units[@]}"; do
  flat="${unit_flat[$path]}"
  branch="${unit_branch[$path]}"
  if [[ "$MODE" == stale ]]; then
    [[ "$flat" -eq 1 ]] && continue   # flat installs have no branch to check
    if [[ -z "${LIVE[$branch]:-}" ]]; then
      to_remove+=("$path"); reasons+=("branch '$branch' not found")
    fi
  else # age
    # Stale when no file in the tree is newer than the cutoff.
    if [[ -z "$(find "$path" -type f -newermt "$CUTOFF" -print -quit 2>/dev/null || true)" ]]; then
      to_remove+=("$path"); reasons+=("no changes in >${DAYS}d")
    fi
  fi
done

if [[ ${#to_remove[@]} -eq 0 ]]; then
  echo "Nothing to clean."
  [[ "$MODE" == stale && $flat_count -gt 0 ]] \
    && echo "(note: $flat_count flat non-branch-scoped install(s) left untouched; use --older-than to prune by age)"
  exit 0
fi

# Total size before we start deleting (works for both dry-run and delete).
total="$(du -shc "${to_remove[@]}" 2>/dev/null | tail -1 | cut -f1 || true)"

# --- act ------------------------------------------------------------------
for i in "${!to_remove[@]}"; do
  path="${to_remove[$i]}"
  why="${reasons[$i]}"
  size="$(du -sh "$path" 2>/dev/null | cut -f1 || true)"
  label="${path#"$INSTALLED"/}"
  if $DO_DELETE; then
    rm -rf -- "$path"
    printf '  removed   %-52s %7s  (%s)\n' "$label" "$size" "$why"
  else
    printf '  would rm  %-52s %7s  (%s)\n' "$label" "$size" "$why"
  fi
done

# Tidy up branch-namespace dirs (e.g. `ls/`, `backport-.../`) left empty.
if $DO_DELETE; then
  find "$INSTALLED" -mindepth 1 -type d -empty -delete 2>/dev/null || true
fi

echo
if $DO_DELETE; then
  echo "Removed ${#to_remove[@]} tree(s), reclaimed ${total:-?}."
else
  echo "${#to_remove[@]} tree(s), ${total:-?} would be removed. Re-run with --delete to apply."
fi
[[ "$MODE" == stale && $flat_count -gt 0 ]] \
  && echo "(note: $flat_count flat non-branch-scoped install(s) left untouched; use --older-than to prune by age)"
exit 0
