# Branch-scoped `.m2` cleanup

Helper for the [`branch-scoped-local-repository`](https://github.com/lenaschoenburg/branch-scoped-local-repository)
Maven extension enabled in [`.mvn/extensions.xml`](../../.mvn/extensions.xml).

That extension installs locally-built artifacts under a per-branch path:

```
~/.m2/repository/installed/<branch>/<groupId>/<artifactId>/<version>/...
```

so parallel worktrees/branches don't clobber each other's `SNAPSHOT`s. The
trade-off is that the `installed/` partition accumulates a tree per branch you
ever build. `clean-installed.sh` prunes it.

## Usage

```bash
# Preview (dry-run) — nothing is deleted without --delete
scripts/branch-scoped-m2/clean-installed.sh --stale-branches
scripts/branch-scoped-m2/clean-installed.sh --older-than=14

# Apply
scripts/branch-scoped-m2/clean-installed.sh --stale-branches --delete
scripts/branch-scoped-m2/clean-installed.sh --older-than=7 --delete
```

### Modes

|         Mode          |                                                                     Removes                                                                     |
|-----------------------|-------------------------------------------------------------------------------------------------------------------------------------------------|
| `--stale-branches`    | Trees whose `<branch>` is no longer a local git branch — including `detached-<sha>` and `worktree-*` entries, which never map to a live branch. |
| `--older-than[=DAYS]` | Trees with no file modified in the last `DAYS` days (default `7`), regardless of branch.                                                        |

### Options

|       Option       |                                                  Meaning                                                  |
|--------------------|-----------------------------------------------------------------------------------------------------------|
| `--delete`, `-f`   | Actually delete. Omit for a dry-run (the default).                                                        |
| `--installed PATH` | Override the partition path (default `~/.m2/repository/installed`).                                       |
| `--repo PATH`      | Checkout used to resolve live branches for `--stale-branches` (default: the repo containing this script). |
| `-h`, `--help`     | Full help.                                                                                                |

## Notes

- **Dry-run first.** Deletion is only performed with `--delete`; without it the
  script just prints what it would remove and the space it would reclaim.
- **Legacy flat installs.** Artifacts installed without a branch prefix (directly
  under `installed/<groupId>/`, e.g. from before the extension was enabled) have
  no branch, so `--stale-branches` leaves them alone. Use `--older-than` to prune
  them by age.
- `--stale-branches` checks **local** branches only (`refs/heads`). A tree for a
  branch you deleted locally but that still exists on the remote is treated as
  stale.
- Compatible with bash 3.2 (stock macOS `/bin/bash`); requires `git` and a `find`
  supporting `-newermt` (GNU and BSD both do — `--older-than` refuses to run,
  rather than over-deleting, if `find` lacks it).

