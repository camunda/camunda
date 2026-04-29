# Event Appliers

Event appliers apply events to state. They run during processing (when events are first appended) AND during replay (when the engine rebuilds state from the log on restart). Replay must produce the *exact* same state mutations as the original processing — otherwise leaders and followers diverge. The `NoChangesTest` golden-file check enforces this, but only for the applier source itself.

## Iron rules

- **Bulk of logic lives in the processor, not here.** Appliers should be as simple as possible — apply event → state change. The processor's job is to compute what the state change should be; the applier just performs it.
- **Released appliers must not change in logic.** Same for any method on a `Mutable*State` interface, and anything transitively called from an applier (helper methods, state-class internals). There is **no golden file** protecting state-class methods — this is a silent danger.
- **Allowed:** cosmetic changes (formatting, imports, comments) and renames where behavior is unchanged. Golden-file diffs from these are fine to accept.
- **Unreleased appliers can be modified freely.** Use the helper script (below) to determine status.

## Pre-edit check

Before editing any applier file or any method reachable from an applier, run the helper script with the file you're about to edit. This works for both applier classes and state class implementations:

```bash
# Applier
.claude/skills/engine-expert/scripts/check-released-applier.sh \
  zeebe/engine/src/main/java/io/camunda/zeebe/engine/state/appliers/UserCreatedApplier.java

# State class implementation reachable from an applier
.claude/skills/engine-expert/scripts/check-released-applier.sh \
  zeebe/engine/src/main/java/io/camunda/zeebe/engine/state/user/DbUserState.java
```

- `RELEASED (first seen in <tag>)` → do not edit logic. Add a new version (for appliers) or a new method on the state class (for state-class methods reachable from an applier) instead.
- `UNRELEASED` → edits are allowed. The first time the file ships in a release tag, it becomes immutable.

The script name says "applier" but the rule applies equally to anything an applier transitively calls — most commonly methods on the state class implementation behind a `Mutable*State` interface. There is no golden file for state classes, so this manual check is the only guard.

## State-class internals: shared-buffer pitfall

State classes commonly read from a `ColumnFamily` and return the value to a caller — or worse, cache it. The returned value is backed by a **shared, mutable buffer** owned by the column family and is overwritten on the next `get(...)` of the same CF. Caching such a value (e.g. into a `Map` keyed by id) silently corrupts data: production incident INC-981 / #16311, where `DbFormState` cached the `persistedForm` returned from RocksDB without copying, and form ids ended up pointing to the wrong form objects.

Two escape hatches:

- `value.copyFrom(stateRead)` into an instance the state class owns (most common pattern).
- `cf.get(key, valueSupplier)` allocates a fresh instance per call instead of returning the shared reference.

Full explanation and rule of thumb: `processors.md` § *Reading state safely*.

## Workflow when behavior must change

1. **Don't edit the existing class.** Create the next version, e.g. `XxxV2Applier.java`.
2. **Register the new version in `EventAppliers`** alongside the existing version.
3. **Forward-port to all newer minor branches before their next release.**
   - First check whether a release branch already exists for any newer minor (skip the fetch if working offline):

     ```bash
     git fetch --prune origin
     git branch -r | grep -E 'origin/release-'
     ```
   - **If a release branch exists** for a newer minor, that minor's code is **already frozen**. Port to BOTH:
     - the release branch (so it ships in that release), and
     - the corresponding stable branch (so it lives in future patches/minors).
       Alert the user explicitly when this case is detected — it's easy to miss.
   - **If no release branch exists** for a newer minor → port to the stable branch only.
   - **Skill-only pitfall** (not yet documented in `docs/zeebe/event-applier-golden-files.md`): a v2 shipped in `8.7.x` patch but missed in `8.8.0` breaks the upgrade path for `8.7.x` → `8.8.0`. The new version must reach every newer minor that ships next.
4. **Format, then run `NoChangesTest`.** A new golden file should appear; create it from the failure message's `cp` command.

## Templates

- Applier: `zeebe/engine/src/main/java/io/camunda/zeebe/engine/state/appliers/UserCreatedApplier.java`
- Registration: `zeebe/engine/src/main/java/io/camunda/zeebe/engine/state/appliers/EventAppliers.java`
- State interface split: `zeebe/engine/src/main/java/io/camunda/zeebe/engine/state/immutable/UserState.java` + `zeebe/engine/src/main/java/io/camunda/zeebe/engine/state/mutable/MutableUserState.java`

## Canonical docs

- `docs/zeebe/event-applier-golden-files.md` — full golden-file guide, back/forward-port cases, allowed changes.
- `zeebe/engine/README.md` § "Event appliers are not allowed to be changed after having been released".

