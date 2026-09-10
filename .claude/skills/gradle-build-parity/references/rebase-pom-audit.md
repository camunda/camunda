# Rebase POM-to-Gradle audit

Use this procedure when a Gradle branch is rebased and the commits replayed from the old branch
may contain Maven `pom.xml` changes. Maven remains the source of truth. The goal is to produce an
actionable port list, including version-catalog work for new external dependencies.

## 1. Identify the correct commit range

A rebased branch commonly tracks the old, pre-rebase remote branch. Start by recording both
references and the graph shape:

```bash
git status --short --branch
tracking=$(git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}')
git log -1 --format='%H %ad %s' --date=iso "$tracking"
git log -1 --format='%H %ad %s' --date=iso HEAD
git rev-list --left-right --count "$tracking"...HEAD
```

Do **not** interpret a status such as `ahead 855, behind 5` as 855 new Gradle commits. After a
rebase, the 855 commits can be the old main history rewritten under the five Gradle commits. Find
the old branch merge-base and count each side explicitly:

```bash
base=$(git merge-base "$tracking" HEAD)
main_ref=${MAIN_REF:-origin/main}
printf 'base=%s\n' "$base"
printf 'base..main=%s\n' "$(git rev-list --count "$base".."$main_ref")"
printf 'base..HEAD=%s\n' "$(git rev-list --count "$base"..HEAD)"
printf 'base..tracking=%s\n' "$(git rev-list --count "$base".."$tracking")"
```

For a branch rebased directly onto `origin/main`, audit `"$base".."$main_ref"` for source Maven
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

## 3. Separate version-only and transient changes

A commit is version-only only when its complete POM diff changes versions and nothing else. Do not
exclude a commit just because it contains a version change: a new dependency, module, exclusion,
plugin configuration, scope, test filter, or code-generation mapping in the same commit still needs
porting.

Inspect the diff manually after using a quick candidate filter. A simple filter is only a review aid
and must not decide the result:

```bash
git show --format= --no-ext-diff --no-textconv --unified=1 "$sha" -- "$pom_path" \
  | grep -E '^(diff --git|@@|[+-][^+-])' \
  | grep -vE '^[+-].*<version>[^<]*</version>[[:space:]]*$' \
  | grep -vE '^[+-].*version\.[A-Za-z0-9_.-]+.*</'
```

Then calculate the final net Maven state once. This collapses add/remove sequences where an
intermediate dependency was later removed and re-added, and prevents porting a state that no longer
exists:

```bash
git diff --no-ext-diff --no-textconv --unified=3 "$base".."$main_ref" -- "$pom_path" \
  > /tmp/rebase-net-pom-diff.patch
```

Examples of sequences that must be reduced to the final state:

- AWS STS was added, removed because the distribution supplied it, then added again when source
  code explicitly used STS. The final Gradle port needs STS, not the intermediate removal.
- A test AssertJ dependency was added and later removed after the test was refactored. Port only the
  dependency that remains in the final POM.
- A test exclusion removed by a later annotation-based fix must not be restored.

Also check the current Gradle file before adding anything. The initial Gradle implementation may
already contain a dependency even though the post-baseline POM diff mentions it:

```bash
rg -n --glob '*.gradle.kts' '<artifact-or-project-name>' .
```

Treat an already-present dependency as verify-only, not as an unconditional duplicate-add task.

## 4. Translate Maven changes to Gradle, including the catalog

For each final POM change, classify it before editing:

1. **New reactor module** — add `include(":<artifactId>")` and its `projectDir` mapping in
   `settings.gradle.kts`; add the module `build.gradle.kts`.
2. **Internal reactor dependency** — add `implementation(project(":<artifactId>"))`, `api`, or a
   test configuration according to the API boundary. Use `api` only when the dependency's classes
   are exposed in the module's public/protected signatures and consumed by code outside the module;
   use `implementation` when the dependency is internal to the module. Do not add an external
   catalog alias for a reactor artifact.
3. **New external dependency** — add a version-catalog alias **and** a Gradle dependency line.
4. **Scope/exclusion change** — map Maven compile/provided/test/optional behavior to the matching
   Gradle configuration and exclusions. Maven compile scope alone is not evidence for `api`; check
   whether dependency types cross the module boundary. Because Gradle `implementation` dependencies
   are absent from consumers' compile classpaths, add a direct dependency to every consumer that
   imports those classes; choose that consumer's `api` versus `implementation` from its own API
   boundary. Check the optional and test-jar rules in `SKILL.md`.
5. **Plugin, profile, test filter, or code-generation change** — mirror it in the appropriate
   convention/module task, or explicitly mark it as a known Maven-only/deferred gap.

### Add external dependencies to the catalog

The catalog is generated in `settings.gradle.kts`; it is not a free-form TOML file. First inspect
whether the artifact or its BOM is already represented:

```bash
# Search by artifact and by group, because aliases are normalized.
rg -n -i '<artifact-id-fragment>|<group-id-fragment>' settings.gradle.kts
rg -n 'version\(|pomVersion\(' settings.gradle.kts
```

Use the Maven source of truth for the version:

```bash
rg -n '<version\.' parent/pom.xml
rg -n '<artifact-id-fragment>|<group-id-fragment>' --glob 'pom.xml' .
```

Choose the catalog declaration as follows:

- A dependency managed by an already imported BOM uses `.withoutVersion()`; for example, AWS SDK
  modules use the existing AWS SDK BOM.
- A dependency whose version is a parent property uses a catalog version sourced with
  `pomVersion("version.<name>")`, then `versionRef(...)` on the library.
- If Maven has an inline version in a module POM, first promote it to a parent property and use
  `${version.<name>}` in Maven. Then source the Gradle catalog from that property.
- Never hardcode a library version in a module `build.gradle.kts`. A genuinely versionless
  dependency still needs a catalog alias if it is referenced as `libs...`.

Typical declarations look like:

```kotlin
version("some-lib", pomVersion("version.some-lib"))
library("com-example-some-lib", "com.example", "some-lib").versionRef("some-lib")

// Dependency managed by a Maven BOM already represented in the catalog.
library("software-amazon-awssdk-new-service", "software.amazon.awssdk", "new-service")
  .withoutVersion()
```

Follow the existing alias naming convention and verify the generated Kotlin accessor by searching
for comparable use sites. For example, `software-amazon-awssdk-new-service` becomes a nested
`libs.software.amazon.awssdk.new.service` accessor. If the dependency is an `io.camunda` artifact,
confirm it is actually a reactor project first; separately released Camunda libraries still belong
in the catalog.

### Compare dependency graphs

After the settings/module entry exists and the Gradle dependency is wired, compare one module at a
time:

```bash
python .claude/skills/gradle-build-parity/compare-module-deps.py --dir <module-dir> --scope compile
python .claude/skills/gradle-build-parity/compare-module-deps.py --dir <module-dir> --scope runtime
python .claude/skills/gradle-build-parity/compare-module-deps.py --dir <module-dir> --scope test --versions
```

Use Maven to explain, rather than blindly fix, a discrepancy:

```bash
./mvnw dependency:list -pl <module-dir>
./mvnw dependency:tree -pl <module-dir> -Dincludes=<group>:<artifact>
```

The comparison can report false Maven runtime extras for classifier variants reached through a
 test-scoped dependency. Confirm the per-artifact Maven scope and the Gradle
`testRuntimeClasspath` before changing the build. A transitive dependency is not a substitute for
a direct dependency when the Java source uses it or Maven declares it explicitly; the direct
relationship is what the Gradle port must preserve.

## 5. Record decisions and validate narrowly

Create a TODO entry with the source commit(s), module/POM path, final behavior, and target Gradle
file. Mark entries that are already present as verify-only. Mark Maven-only release/plugin behavior
as deferred rather than silently dropping it.

For a build change, use the affected module only:

```bash
./gradlew :<project>:compileJava --configuration-cache
./gradlew :<project>:test --tests '<TestClass>' --configuration-cache
python .claude/skills/gradle-build-parity/compare-module-deps.py <gradle-project> --scope test --versions
```

The commands above show the Gradle task shape for the parity investigation. Do not run a
full-repository experiment for every POM commit.
For a documentation-only audit, no build is necessary, but run `git diff --check` on the generated
TODO/reference file.

## Edge cases observed in the rebase audit

- The tracking remote can make a rebased branch appear hundreds of commits ahead and a few commits
  behind. Use the merge-base and explicit counts; the upstream status alone is insufficient.
- `origin/main..HEAD` after the rebase contains only the local Gradle commits, so it misses the main
  commits that were replayed under them.
- POM history includes version-only Renovate commits, merge commits, and commits whose POM change is
  later reverted. Count them, but port only the final non-version behavior.
- XML diff presentation can hide ordinary additions/removals unless `--no-textconv` is used.
- A new parent-POM dependency has two independent Gradle tasks: add the module dependency and add
  the catalog alias. Missing either one causes a compile failure or violates catalog parity.
- Gradle `implementation` is not compile-transitive to consumers, unlike Maven's usual compile
  dependency propagation. A Maven consumer that imports a provider's transitive dependency may need
  an additional direct Gradle declaration; this is expected and is not a reason to make the
  provider's dependency `api`.
- A POM plugin setting can be important to Maven publication but have no Gradle equivalent in the
  current scope (flattened POMs, source/javadoc attachment, dependency analysis). Keep these in a
  deferred section so they remain visible.
- A new dependency may be supplied transitively in Gradle and therefore not fail compilation. Keep
  the explicit Gradle declaration anyway when Maven declares it directly, then verify `api` versus
  `implementation` rather than relying on the transitive path.
