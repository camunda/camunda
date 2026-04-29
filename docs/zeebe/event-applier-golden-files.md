# Event Applier Golden Files

## Background

Event appliers in Zeebe run in two contexts: during **processing** (when events are initially
appended to the log) and during **replay** (to rebuild state from the event log). Replay must
produce the exact same state mutations as the original processing — if it doesn't, leaders and
followers can end up with divergent state on updates, which would be a critical bug.

Golden files enforce this invariant by snapshotting the source code of each event applier. The
`NoChangesTest` in `EventAppliersTest` compares each applier's current source against its golden
copy and fails when they differ. This catches accidental or unreviewed changes before they reach
production.

## Rules of thumb

**It's always safe to add a new event applier version**. Instead of changing an existing event
applier, register a new version. It's usually the safer choice and does not cost much effort.

**Always keep event applier versions aligned across minor versions**. If you add a new event
applier version in a minor version, add it to all newer minor versions as well. This allows users
to safely update to the next minor version without any breaking changes in replay.

> [!WARNING]
> **Beware of minor release code freezes.** During the release of a new minor version, its source
> code may already be frozen while patches of older minor versions continue to ship. If your new
> event applier version makes it into a patch of an older minor but does not make it into the new
> minor's initial release, the upgrade path from that patch to the new minor is broken. The new
> minor will not know how to replay the new applier version, which is unrecoverable without an
> ad-hoc patch.
>
> Either hold off on porting new event applier versions to stable branches during a minor code
> freeze, or ensure your port is included in the minor release.
>
> 💡 **Example:** This happened during the 8.9.0 release. `ProcessEvent.TRIGGERING` v2 was
> backported to `stable/8.8` (released in 8.8.22) and to `stable/8.9`, but was not included in
> 8.9.0. Clusters updating from 8.8.22 to 8.9.0 hit dead partitions, unrecoverable until 8.9.1.

## Common cases

The following scenarios where the test fails are common and should be straightforward to resolve.

### I added a new event applier (or a new version)

Create the golden file. The test failure message includes a ready-to-run `cp` command. Just
copy-paste it. Adding a new event applier is always safe.

### I intentionally changed an existing event applier

**Don't update the golden file.** Register a new applier version instead. The previous version must
remain unchanged so that older events replay correctly. Ensure the new version is registered in all
newer minor versions as well.

For exceptions, see the ["Allowed Changes"](#allowed-changes) section below.

## High-stakes back- and forward porting cases

These cases can occur when you port an event applier from one Zeebe version to another and the
golden files differ. It's important that an older Zeebe version can update to a newer Zeebe version
and can then replay all existing events in the exact same way as before. If the golden files differ,
this means that the older Zeebe version and the newer Zeebe version will replay the same events
differently. This is a severe problem that we must resolve carefully.

> [!WARNING]
> These cases require careful consideration! Please read the instructions below with attention if
> you find yourself in one of these scenarios. If you are unsure how to proceed, ask for help.

### Backported applier (newer → older version)

If you ported an applier from a newer Zeebe version to an older one and the golden file differs, you
may have found a **critical bug**. What happens now depends on whether the newer Zeebe version was
already released or not:

#### Not yet released

If it was not yet released, you can still align the newer Zeebe version with the older Zeebe version
by updating the golden file in the newer Zeebe version to match the older Zeebe version. This is
safe because the newer Zeebe version has not yet been released, so you can still change its behavior
without breaking production.

🔧 **Action:** Ensure that the newer Zeebe version aligns with the older Zeebe version first. Do not
adjust event appliers in the older Zeebe version. All event appliers known to the older Zeebe
version must be copied to the newer Zeebe version. Next, you can introduce the intended changes to
both versions.

#### Already released

If the applier in the newer Zeebe version was already released with different behavior, then you
cannot align the newer Zeebe version with the older Zeebe version by just overwriting them.

🔧 **Action:** Instead, keep the misalignment **and** register a new event applier version in the
older version such that you can also add those same versions to the newer version. This way, the
older Zeebe version can still safely update to the newer Zeebe version without any breaking
changes for any recently written events. The misalignment will remain for some specific event
applier versions, but it is contained. Note that we require users to update to the latest patch
of a minor version before they can update to the next minor version. So hopefully, this
misalignment does not pose problems. Once you've aligned the currently used appliers, you can
introduce the intended changes to both versions. Lastly, we should consider documenting that
snapshots should be taken on all partitions before updating to the newer Zeebe version to ensure
that no misaligned events have to be replayed.

💡 **Example:** You backport a newly created event applier (A v2) from a newer Zeebe version to an
older Zeebe version, but the golden file differs from the older version's applier (A v2 already
exists here and is different). The newer Zeebe version was already released with the different A v2,
so you cannot align the newer Zeebe version with the older Zeebe version by just overwriting the
golden file in the newer Zeebe version. Instead, you keep the misalignment and register a new event
applier version (A v3) as a copy of A v2 in the older Zeebe version. You also add A v3 to the newer
Zeebe version. Next, you can introduce the intended changes to both versions in A v4. Any events of
A v3 will be replayed the same way in both versions, so there are no breaking changes for any
recently written events. The misalignment of A v2 will remain, but it is contained. Lastly, document
the warning mentioned above about snapshots.

### Forward-ported applier (older → newer version)

If you ported an applier from an older version to a newer one and the golden file differs, you may
have found a **critical bug** — the newer version may already be running different behavior in
production.

#### Not yet released

If it was not yet released, you can still align the newer Zeebe version with the older Zeebe version
by updating the golden file in the newer Zeebe version to match the older Zeebe version. You may
need to reintroduce some of the event appliers already present in the newer Zeebe version as new
event applier versions. This is safe because the newer Zeebe version has not yet been released, so
you can still change its behavior without breaking production.

🔧 **Action:** Ensure that the newer Zeebe version aligns with the older Zeebe version first. Do not
adjust event appliers in the older Zeebe version. All event appliers known to the older Zeebe
version must be copied to the newer Zeebe version. Next, you can reintroduce any newer event applier
changes as new event applier versions in the newer Zeebe version.

#### Already released

If the applier in the newer Zeebe version was already released with different behavior, then you
cannot align the newer Zeebe version with the older Zeebe version by just overwriting them.

🔧 **Action:** Instead, you should keep the misalignment and register new event applier versions in the
newer version such that you can also add those same versions to the older version. This way, the
older Zeebe version can still safely update to the newer Zeebe version without any breaking changes
for any recently written events. The misalignment will remain for some specific event applier
versions, but it is contained. Note that we require users to update to the latest patch of a minor
version before they can update to the next minor version. So hopefully, this misalignment does not
pose problems. Once you've aligned the currently used appliers, you can introduce the intended
changes to both versions. Lastly, we should consider documenting that snapshots should be taken on
all partitions before updating to the newer Zeebe version to ensure that no misaligned events have
to be replayed.

## Allowed changes

In rare cases, updating the golden file is acceptable:

- **Cosmetic changes**: comments, formatting, import reordering — anything that doesn't affect
  runtime behavior.
- **The change does not lead to different state mutations**, i.e. you guarantee that existing events
  will still replay to the same state, e.g. because you know they cannot contain a newly added
  optional field and the event applier logic is designed to handle the field being absent. Even in
  this case, consider carefully whether it's truly safe to update the golden file.

When in doubt, register a new applier version. A new version is usually the safer choice and does
not cost much effort.

## Bulk Updates with GoldenFileUpdater

> [!WARNING]
> `GoldenFileUpdater` overwrites **all** golden files unconditionally. Running it without reviewing
> each failure individually can hide breaking changes that cause leader/follower state divergence in
> production.

`NoChangesTest` contains an inner class `GoldenFileUpdater` with a `main` method. It iterates all
registered appliers and copies each source file to its golden file (or creates an empty file for
NOOP appliers). You can run it from your IDE (IntelliJ shows a run gutter icon) or from the command
line. It will prompt for confirmation before overwriting any files.

Only use this when you have many new golden files to create at once — for example, after adding
several new appliers. Always review each failing test case first to confirm that updating the golden
file is the right action.
