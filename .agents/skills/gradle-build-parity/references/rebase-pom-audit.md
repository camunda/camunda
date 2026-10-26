# Rebase POM audit

Use this procedure only when a Gradle branch has been rebased and it is unclear which Maven
changes were replayed onto it. Maven remains the source of truth. The result of this audit is a
final, actionable POM diff; use [pom-change-porting.md](pom-change-porting.md) to apply that diff
to Gradle.

After a normal merge, or when the relevant Maven changes are already known, this audit is usually
not necessary. Go directly to the porting guide.

## 1. Identify the correct commit range

A rebased branch can track the old, pre-rebase remote branch. Start by recording both references
and the graph shape:

```bash
git status --short --branch
tracking=$(git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}')
git log -1 --format='%H %ad %s' --date=iso "$tracking"
git log -1 --format='%H %ad %s' --date=iso HEAD
git rev-list --left-right --count "$tracking"...HEAD
```

Do not interpret a status such as `ahead 855, behind 5` as 855 new Gradle commits. After a rebase,
the 855 commits may be old main history rewritten underneath five local Gradle commits. Find the
old branch merge-base and count each side explicitly:

```bash
base=$(git merge-base "$tracking" HEAD)
main_ref=${MAIN_REF:-origin/main}
printf 'base=%s\n' "$base"
printf 'base..main=%s\n' "$(git rev-list --count "$base".."$main_ref")"
printf 'base..HEAD=%s\n' "$(git rev-list --count "$base"..HEAD)"
printf 'base..tracking=%s\n' "$(git rev-list --count "$base".."$tracking")"
```

For a branch rebased directly onto `origin/main`, audit `"$base".."$main_ref"` for Maven source
changes. Audit `"$main_ref"..HEAD` separately only for local Gradle commits; do not re-port the
Gradle implementation commit itself. Validate the assumption before continuing:

```bash
git merge-base --is-ancestor "$main_ref" HEAD && echo 'HEAD is based on main'
git log --reverse --format='%H%x09%ad%x09%s' --date=short "$base".."$main_ref" | head
```

If `origin/main` is not an ancestor, stop and choose the actual post-rebase base. Never fetch or
rewrite the branch as part of this audit unless explicitly requested.

## 2. Enumerate POM-touching commits

Use a glob pathspec that includes the root POM and nested module POMs:

```bash
pom_path=':(glob)**/pom.xml'
git log --reverse --format='%H%x09%ad%x09%s' --date=short \
  "$base".."$main_ref" -- "$pom_path" > /tmp/rebase-pom-commits.tsv
wc -l /tmp/rebase-pom-commits.tsv
cat /tmp/rebase-pom-commits.tsv
```

For every candidate, record the exact POM paths and a compact diff:

```bash
while IFS=$'\t' read -r sha date subject; do
  printf '\n===== %s %s %s\n' "$sha" "$date" "$subject"
  git diff-tree --no-commit-id --name-status -r "$sha" -- "$pom_path"
  git show --format= --no-ext-diff --no-textconv --unified=0 "$sha" -- "$pom_path"
done < /tmp/rebase-pom-commits.tsv
```

`--no-textconv` matters in this repository: XML has a configured diff presentation that can turn a
normal patch into an `XML` side-by-side report. The raw diff is much easier to classify.

Merge commits need special care. `git log -- <path>` can list a merge even when
`git diff-tree -r <merge>` shows no ordinary diff because a merge has multiple parents. Inspect its
parents and the commits on the merged side; do not create a duplicate TODO entry merely for the
merge commit:

```bash
git show -s --format='%H%n%P%n%s' "$sha"
git show --cc --no-ext-diff --no-textconv --unified=0 "$sha" -- "$pom_path"
```

## 3. Reduce the result to the final net POM state

A commit is version-only only when its complete POM diff changes versions and nothing else. Do not
exclude a commit just because it contains a version change: a new dependency, module, exclusion,
plugin configuration, scope, test filter, or code-generation mapping in the same commit still needs
porting.

Inspect candidates manually after using a quick filter. The filter is only a review aid and must
not decide the result:

```bash
git show --format= --no-ext-diff --no-textconv --unified=1 "$sha" -- "$pom_path" \
  | grep -E '^(diff --git|@@|[+-][^+-])' \
  | grep -vE '^[+-].*<version>[^<]*</version>[[:space:]]*$' \
  | grep -vE '^[+-].*version\.[A-Za-z0-9_.-]+.*</'
```

Calculate the final net Maven state once. This collapses add/remove sequences where an
intermediate dependency was later removed and re-added:

```bash
git diff --no-ext-diff --no-textconv --unified=3 "$base".."$main_ref" -- "$pom_path" \
  > /tmp/rebase-net-pom-diff.patch
```

Examples of sequences that must be reduced to the final state:

- AWS STS was added, removed because the distribution supplied it, then added again when source
  code explicitly used STS. The final Gradle port needs STS, not the intermediate removal.
- A test AssertJ dependency was added and later removed after the test was refactored. Port only
  the dependency that remains in the final POM.
- A test exclusion removed by a later annotation-based fix must not be restored.

Also check the current Gradle files before creating porting work. The initial Gradle
implementation may already contain a dependency mentioned by the POM diff:

```bash
rg -n --glob '*.gradle.kts' '<artifact-or-project-name>' .
```

Treat an already-present dependency as verify-only, not as an unconditional duplicate-add task.

## 4. Hand off to the porting guide

For every final, non-version-only Maven behavior change, record:

- the source commit(s) and POM path;
- the final Maven behavior after reverted/intermediate changes are collapsed;
- the target Gradle project and build file; and
- whether the Gradle side is missing, already present and needs verification, or intentionally
  deferred as Maven-only behavior.

Then follow [pom-change-porting.md](pom-change-porting.md), one final change at a time. Do not
port the history mechanically: the final Maven state and the current Gradle state are what matter.

## Rebase-specific edge cases

- The tracking remote can make a rebased branch appear hundreds of commits ahead and a few commits
  behind. Use the merge-base and explicit counts; upstream status alone is insufficient.
- `origin/main..HEAD` after the rebase contains only local Gradle commits, so it misses the main
  commits replayed underneath them.
- POM history includes version-only Renovate commits, merge commits, and commits whose POM change
  is later reverted. Count them, but port only the final non-version behavior.
- XML diff presentation can hide ordinary additions/removals unless `--no-textconv` is used.
