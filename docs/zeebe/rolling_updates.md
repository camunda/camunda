# Rolling Updates

Starting with 8.5.0, Zeebe officially supports rolling updates.

## Breaking Changes

To support rolling updates, we have to ensure that all patch versions of the previous or current minor are compatible with the development version.

### Network

Old and new versions need to be able to communicate with each other.
Most importantly, they need to be able to form a raft replication group, and update cluster membership via SWIM.
The command API should also remain compatible between old and new versions but exceptions can be made.
Breaking command API compatibility drastically increases the impact of rolling updates because it may block all processing until the rolling update is finished.

Compatibility can be achieved differently, depending on the use case.
For example:
1. New version supports both old and new protocol and can automatically choose which to use.
2. New version supports a new protocol but doesn't use it by default.
3. New version detects an old version and has explicit update logic.

The first option is the classic solution to evolve network protocols.
To make this possible, we recommend to always version protocols, for example by prefixing all messages with a single integer version.
This makes it possible for old versions to detect newer protocol versions and for new versions to support both protocol versions at the same time.
See `AbstractMessageDecoder` and its implementations for an (old) example.
Details are documented in our [messaging](messaging.md#protocol-format) docs.

The second option should be avoided if possible because it requires more effort.
Switching to a new protocol requires a configuration change, this has a higher impact than the rolling update itself.
Additionally, the old protocol has to be deprecated and can only be removed later.
When removing a deprecated protocol, there is the risk that unaware users encounter problems during the rolling update.

The third option is the one we chose when implementing dynamic scaling, where new versions would communicate via a new gossip protocol that old versions did not support.
The new version avoids the need to communicate with an old version because it can assume the state of the old version and make decisions without communicating.
See `RollingUpdateAwareInitializerV83ToV84` for implementation details.

### Protocol

The [Zeebe protocol](../../zeebe/protocol/src/main/resources/protocol.xml) defines both the network protocol between gateways and brokers as well as the on-disk serialization of records.
The protocol is defined as [SBE](https://www.fixtrading.org/standards/sbe-online/), a binary encoding with limited support for backwards compatibility.
We must be careful when extending or modifying the protocol to ensure that we do not break compatibility.
Because SBE is a binary encoding with relatively little overhead, accidentally breaking compatibility can result in silent data corruption when data is read the wrong way.
See https://github.com/camunda/camunda/issues/14957 for an example of such an issue.

There is guidance on message versioning that explains some of the rules we have to follow to ensure compatibility: https://github.com/real-logic/simple-binary-encoding/wiki/Message-Versioning
Additionally, we have automated tests that prevent changes until we [mark them as acceptable](../../zeebe/protocol/revapi.json).

### Processing

Processing is ideally compatible between old and new versions.
This is not always possible, otherwise we would not be able to implement new features or fix bugs.
All changes we make should fall into one of two categories:
1. Adding new commands and events, typically to implement new features.
2. Changing command behavior to produce different sequence of events, typically to fix bugs or extend features.

When adding new commands or events, we break compatibility with older brokers because they cannot process or replay them.
This is a transient state during rolling updates and resolves itself once the old broker is updated.

When changing command behavior, older brokers can simply continue replaying the new sequence of events produced.
When the old broker is processing, it will continue to produce the old sequence of events and new brokers will have to replay it.

It is _not allowed_ to change behavior of events because we must ensure that the state of old and new brokers do not diverge.
If we were to change how an event behaves, old brokers would apply this event differently to new brokers, resulting in a different runtime state.
This can lead to serious bugs that are difficult to diagnose.

Instead, we either have to introduce _new_ events or add a new _version_ of an existing event.
Both prevent diverging states because old brokers will stop processing or replaying when encountering new events or new versions.

> [!WARNING]
> We currently have no mechanism to enforce that event behavior is not modified so we rely on developer awareness.

## Data migrations

Data migrations are applied every time a new version opens a snapshot taken by an old version.
In the past we used data migrations liberally to implement new features and to fix broken data that was caused by bugs.

As we learned after the implementation of multi-tenancy, data migrations incur a high cost.
Especially for critical use cases that have accumulated a lot of runtime state, rewriting large parts of the state is prohibitive.
On the one hand, these migrations take a long time.
This increases the risk of rolling updates because it delays recovery and increases the risk that manual intervention is needed.
For example, a rolling update as implemented by Kubernetes will not complete if data migrations take so long that the updated brokers don't become ready in time.
On the other hand, the migrations require additional resources that may not be accounted for.
This can show up in CPU but most importantly in memory and disk resources.
See for example https://github.com/camunda/camunda/issues/14975.

When implementing a new feature, we actively try to avoid data migrations because their impact is difficult to assess for all use cases.
Instead, we try to find alternative ways to implement new features:

1. Staying backwards compatible and writing data to a new column family while reading it from both old and new.
2. Staying backwards compatible by mixing old and new data in the same column family.
3. Incrementally moving data from old to new column family on first access.
4. Using secondary column families that contain only the data needed for the new feature.

Avoiding full migrations of entire column families has a cost when implementing new features.
We accept this cost because it is difficult to assess the potential impact on different use cases and setups.

## Testing

You can find a description of the rolling update tests in the [as part of our testing guide](/docs/testing/acceptance.md#rolling-update-tests).

