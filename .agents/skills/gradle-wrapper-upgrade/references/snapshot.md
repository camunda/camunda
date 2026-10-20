# Snapshotting a wrapper outside git

Step 3 of `SKILL.md` sends you here when `git status --short gradlew gradlew.bat gradle/wrapper` printed something, or the project is not in git at all. Either way git cannot give the four files back, so you need a copy before the upgrade touches them.

## Take the copy

One command, from the project root:

```
SNAP=$(mktemp -d) && mkdir -p "$SNAP/gradle/wrapper" \
  && cp gradlew gradlew.bat "$SNAP/" \
  && cp gradle/wrapper/gradle-wrapper.{properties,jar} "$SNAP/gradle/wrapper/" \
  && echo "snapshot: $SNAP" && find "$SNAP" -type f
```

**There are two `cp` commands because they have two different targets**, and that is the load-bearing detail. Merging them into one call with four sources and `"$SNAP/"` as the target does not do the same thing: `cp` with several sources and a directory target puts *every* source in that directory, so the two nested files land flat at the snapshot root. Do not rewrite, reformat or "simplify" the pair into a single `cp`.

**Do not suppress stderr here.** No `2>/dev/null`, no `|| true`. A snapshot that half-failed silently is worse than one that failed loudly, because the failure only surfaces later as files written into the user's project.

**Check the shape before going on.** That trailing `find` must print exactly four paths, two of them under `gradle/wrapper/`:

```
/tmp/tmp.XXXX/gradlew
/tmp/tmp.XXXX/gradlew.bat
/tmp/tmp.XXXX/gradle/wrapper/gradle-wrapper.properties
/tmp/tmp.XXXX/gradle/wrapper/gradle-wrapper.jar
```

More than four paths, or `gradle-wrapper.properties` or `gradle-wrapper.jar` sitting at the snapshot root, means the snapshot is malformed. **Delete it and take it again** before doing anything else. Restoring is `cp -a "$SNAP"/. .`, which faithfully reproduces whatever shape it finds — so a flattened pair in the snapshot becomes a flattened pair in the user's project root, and a rollback that is otherwise perfect still leaves junk behind.

**Write down the path it prints.** Shell variables do not survive into your next command, so `$SNAP` is empty by the time you would restore — and unset, `cp -a "$SNAP"/. .` becomes `cp -a /. .`, which copies the root of the filesystem into the project. Use the literal path from here on.

**All four files, not just the properties.** Run 1 of the `wrapper` task rewrites the scripts and jar from the old templates and run 2 from the new, so a failure between them leaves a mixture. Reverting `gradle-wrapper.properties` alone moves the version back while leaving the new `gradlew` and jar in place — which is the half-done state Step 5 exists to detect, reached while trying to undo one.

**Outside the project.** `mktemp -d` is outside it; a `.bak` beside the original is not, and is a file you have added to the user's tree. Delete the snapshot once Step 5 passes.

## Verify against it

Anywhere `SKILL.md` or `references/rollback.md` checks state with `git status --short`, use the snapshot instead: `diff -r <snapshot> .` over the four paths. After a successful upgrade all four must differ; after a rollback none may.

## Restore from it

```
cp -a /tmp/tmp.XXXX/. .
```

**Do not expand that into a list of the four files.** `cp` with several sources and a directory target puts *every* source in that directory, so naming them individually drops `gradle-wrapper.properties` and `gradle-wrapper.jar` into the project root while `gradle/wrapper/` keeps the failed upgrade — two stray files, a wrapper never actually restored, and a canary that now fails for an unrelated reason. The trailing `/.` copies the tree instead, putting each file back where it came from.
