# Rolling back a blocked upgrade

Step 6 of `SKILL.md` sends you here: the `wrapper` task failed, or it succeeded and the canary failed. The target version cannot run this build. Put the project back exactly as you found it and tell the user what you learned — no retrying the upgrade, no editing build scripts to force it through.

## 1. Restore all four files

```
git checkout -- gradlew gradlew.bat gradle/wrapper
```

That is the whole step, and it cannot be got wrong.

If Step 3 had you take a copy instead — a dirty tree, or no git — restore from it with `cp -a "$SNAP"/. .` as `references/snapshot.md` describes. **Do not expand that into a list of the four files.** `cp` with several sources and a directory target puts *every* source in that directory, so naming them individually drops `gradle-wrapper.properties` and `gradle-wrapper.jar` into the project root while `gradle/wrapper/` keeps the failed upgrade.

**That is the same hazard as the one in `snapshot.md`, at the other end.** Flattening while taking the copy and flattening while restoring it produce the identical mess, and `cp -a` replays whatever shape the snapshot has — so if you did not run that file's `find` shape check when you took it, run it now, before restoring. A restore cannot be more correct than the snapshot it reads.

## 2. Confirm the restore, then re-run the canary

`git status --short gradlew gradlew.bat gradle/wrapper` prints nothing — or, from a snapshot, `diff -r` is silent and `distributionUrl` names the original version.

Then run the Step 3 canary command again — the same form, `tasks` or `tasks --all`. It passed then and must pass now, which is what proves you restored a working build rather than four plausible-looking files. A rollback you did not verify is the same as none.

**If this canary fails, you are not done — you have a bad restore, not a second finding.** Fix the restore and re-run until it is green. Only then delete the snapshot, and do not write the report before that point.

## 3. Report — four things, all of them, in the closing message

Put all four in the message that ends your turn, not scattered through earlier ones: the last message is the one the user reads. **A report missing any of the four is a failed report**, however well the rollback itself went.

**Once you have restored, the upgrade did not happen.** No wording of this report may say it succeeded — not "upgraded to 9.0.0", not "regenerated the wrapper files", not a changelog of what the `wrapper` task wrote before you undid it. Those describe a state you deliberately threw away, and the user will read them as the outcome.

**Re-read every fact from the project as it stands now.** Do not quote a `--version` or canary result captured earlier in the turn: a failed run 2 and a bad first restore both produce readings that were true when taken and false by the time you write. Run `./gradlew --version` and re-read `distributionUrl` *after* the last restore; where they disagree with what you were about to write, they are right.

1. **The verdict.** *This build does not appear to be upgradeable to Gradle 9.0.0* — stated plainly and first, followed by the version the project is on now, taken from that re-read. Do not open with the diagnosis or the file list; open with whether the thing they asked for happened.
2. **The error.** Quote it. "`Could not find method exec()`" tells the user which removal bit them; "the upgrade failed" tells them nothing.
3. **The next rung — a sentence either way.** Name the next release after the one the project is on (from 8.5, that is 8.6), and say where it leads: one release at a time up to the version they asked for.

   **Write it as a single path, not two recommendations.** These are adjacent sentences from a real report:

   > The smallest next hop is Gradle 8.6. Upgrade to the latest Gradle 8.x release and resolve the deprecations before retrying.

   The first sentence is right and the second is wrong, and together they name two different versions, so the user cannot tell which to follow. **"Upgrade to the latest 8.x" is not the advice** — that is the same oversized jump that just failed, only from a different starting point. Say instead: *"Go to 8.6 next. From there 8.7, then 8.8, and so on — one release at a time, running `./gradlew --warning-mode=all build` at each step to clear what the next one removes — until you reach 9.0.0."* One route, one rung at a time.

   When there is no next release — the request was already the next one or smaller, or the project is on the newest release — **say that instead**. Silence is never the right content here, and this is the item that goes missing, because it is the only one that can be dropped without leaving a visible hole.
4. **The links.** Release notes for the version they asked for, `https://docs.gradle.org/<version>/release-notes.html` — for 9.0.0, https://docs.gradle.org/9.0.0/release-notes.html — the per-version record of what changed, and where the cause is most likely written down. For a major, add the upgrade guide (https://docs.gradle.org/current/userguide/upgrading_major_version_9.html for 9.x), which lists the removals against their replacements and is the only way to size the work.

**Recommend the hop; do not take it.** Quietly landing the user on a version they did not ask for, right after telling them the one they did ask for failed, is a second unrequested change on top of a failed first. Offer it and wait — which makes item 3 a sentence you write rather than a command you run, and is exactly why it is the easiest to skip.

## The ladder — one release at a time

Resolve the next rung rather than guessing it:

```
curl -sL https://services.gradle.org/versions/all    # every release, newest first
```

**Count only final releases** — the feed carries release candidates, milestones and nightlies too. An entry is final when `snapshot`, `nightly` and `broken` are false and `rcFor` and `milestoneFor` are empty. Offering `8.6-rc-1` as the safe next step is worse than offering nothing.

**The rung is always the next release after the version the project is on — never a fixed waypoint.** From 8.5 it is 8.6. If the user takes 8.6 and the jump from *there* to 9.0.0 fails the same way, the next recommendation is **8.7** — not 8.6 again, and not a leap to the end of the line. Every failure moves the ladder up exactly one rung, and the target the user originally asked for never changes.

One release at a time is the smallest step that makes progress, and a failure on one rung is a far smaller thing to diagnose than a failure across several.

**Never recommend skipping to the newest release of the current major.** "Get to the latest 8.x, then try 9.0" is the same oversized jump that just failed, restarted from a different rung — 8.5 → 8.14 crosses nine releases' worth of removals in one step, and when it breaks the user is no better off than they are now. The whole point of the ladder is that each step is small enough that a failure names its own cause.

**Clear deprecations on every rung, not at the end.** Each release warns about what the next ones remove, which is how the ladder stays cheap:

```
./gradlew --warning-mode=all build
```

Climb until the project is on the version they originally asked for.
