---
title: Renaming or removing a released record property breaks existing logs and custom exporters
issue: https://github.com/camunda/camunda/issues/71137
kind: Backward compatibility
status: Mitigated
summary: >
  Renaming, removing or retyping a released record property breaks custom exporters (compile error or
  NoSuchMethodError) and the records already in the log, which old followers cannot replay.
  Deprecate and add a new property instead.
applies_when:
  - You rename, remove or change the type of a getter in a `*RecordValue` interface (`zeebe/protocol`), or of a property or its serialized key in a record class (`zeebe/protocol-impl`), and the property was in a released version.
  - You add an entry to `zeebe/protocol/ignored-changes.json` or `revapi.json`.
  - You edit or delete a line in a file in `zeebe/protocol-impl/src/test/resources/protocol/records/golden`.
does_not_apply_when:
  - You change a property that was never released.
---

# Renaming or removing a released record property breaks existing logs and custom exporters

## Where

- `zeebe/protocol`: the public record value interfaces (`*RecordValue`), which are the exporter API, and the `revapi` configuration (`revapi.json`, `ignored-changes.json`).
- `zeebe/protocol-impl`: the record classes that serialize the properties into the log, and their golden files in `src/test/resources/protocol/records/golden`.
- Triggering changes: renaming, removing or changing the type of a getter or a property, or changing the serialized key of a property, when it was in a released version.

## What can go wrong

- **Custom exporters break.** Record implementations are handed to exporters directly.
  An exporter that calls a removed or renamed getter fails to compile against the new `zeebe-protocol`, or throws `NoSuchMethodError` at runtime.
  The users find out when they upgrade.
- **The log is read wrongly.** Records already written keep the old property name.
  After a rename they are read as if the property was missing, and an old follower replaying a record written by a new leader does not know the new name.
- Both are silent for tests that only write and read records with the new name.

Precedent: [#54823](https://github.com/camunda/camunda/issues/54823).
`getMessageKeys()` was removed from `MessageBatchRecordValue`.
The justification only considered log replay, where unknown fields are ignored, and missed custom exporters.
The method was restored afterwards.

## What to do

Deprecate and add a new property, never rename or remove a released one. Do not edit a golden file or bypass `revapi` to make the build pass.

1. Keep the released property and its getter. Mark it `@Deprecated` and document the replacement.
2. Add the new property next to it, with a default value. Write both, and read the old one when the new one is missing, as long as old records can still be in the log.
3. Remove the deprecated property only in a major version (see Exceptions).
4. When `RecordGoldenFilesTest` reports a new property, check that it has a default value, then update its golden file as the test output says.
5. Never update a golden file, or add an entry to `zeebe/protocol/ignored-changes.json` or `revapi.json`, to get a build green for a removed, renamed or retyped property. These failures are the signal that this risk applies.
6. In review, check every change to `zeebe/protocol` and `zeebe/protocol-impl` for removed or renamed properties, every changed golden file, and every new bypass entry.

## Exceptions

There are no exceptions for a released property.
A change to it does not only break old behavior: it prevents upgrades, replay and exporters from working, which is never acceptable.
The only ways to remove or change a released property are:

- **A major version.** Remove the deprecated property only there.
  Give the matching entry in `ignored-changes.json` a justification that covers exporters as well as log replay.
- **The property was never released.** Then this risk does not apply.
  Check the release history of the file before treating a property as unreleased.
  The golden file test says the same: properties may only be modified if they are not in any released version.

If a case seems to need anything else, ask in [#top-c8-engine-governance](https://camunda.slack.com/archives/C0BPZFRT19N) before changing the property.

## How to detect

- A diff that removes, renames or retypes a getter in a `*RecordValue` interface, or a property in a record class in `zeebe/protocol-impl`.
- A changed or deleted line in a golden file, or a new entry in [`zeebe/protocol/ignored-changes.json`](../../../zeebe/protocol/ignored-changes.json) or [`revapi.json`](../../../zeebe/protocol/revapi.json).
- A failure of one of the two tests below. Neither can tell a justified change from an unjustified one, because the golden file or the bypass entry can simply be updated. See also the protocol section of [rolling updates](../rolling_updates.md#protocol).

## Coverage bar

The risk is Mitigated: two permanent, automated checks fail the build when a released property is removed, renamed or retyped. They must stay in place.

**`revapi` on `zeebe/protocol`** (exporter API)

- Configured in `parent/pom.xml` (`revapi-maven-plugin`, goal `check`) and in `zeebe/protocol/revapi.json`. Runs in the `verify` phase of the module: `./mvnw -pl zeebe/protocol verify`. It is skipped with `-DskipChecks`.
- Compares the public API of `zeebe-protocol` with the published version in `backwards.compat.version` of `parent/pom.xml`. A removed or renamed getter, or a changed type, in a `*RecordValue` interface fails the build. The package `io.camunda.zeebe.protocol.management` is excluded.
- Accepted changes are listed with a justification in `zeebe/protocol/ignored-changes.json` and `revapi.json`.

**`RecordGoldenFilesTest` in `zeebe/protocol-impl`** (serialized records)

- `zeebe/protocol-impl/src/test/java/io/camunda/zeebe/protocol/impl/record/value/RecordGoldenFilesTest.java`. Run it with `./mvnw -pl zeebe/protocol-impl test -Dtest=RecordGoldenFilesTest`.
- Every class under `zeebe/protocol-impl/src/main/java/io/camunda/zeebe/protocol/impl/record` has a golden file in `zeebe/protocol-impl/src/test/resources/protocol/records/golden`. The test reads the source of the class, extracts its properties (name, type, serialized key, whether it has a default) and compares them with the golden file.
- It fails when: a class has no golden file, a property of the golden file is missing in the class, a property has another type or another serialized key, or a property is new and not in the golden file. A new property must have a default value, and then its golden file is updated.
- It reads the source text with patterns, not the compiled classes, so a property declared in an unusual way may not be seen. A key expression it cannot resolve is compared as text.

**Gap**

- Both checks pass when the golden file or the bypass entry is edited in the same change. Nothing flags those edits for review.
- The risk would count as Resolved when there is structurally no way to change the records in a way that breaks upgrades, replay or exporters.

## References

- [#54823](https://github.com/camunda/camunda/issues/54823)
- [Rolling updates](../rolling_updates.md)
