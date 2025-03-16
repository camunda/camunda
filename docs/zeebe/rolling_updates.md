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

### Continuous Integration

Rolling updates are testing mainly through `RollingUpdateTest` in the qa/update-tests module.
These tests run in three different modes, depending on the environment:

1. **Local**: Checks compatibility between the current version and the first patch of the previous minor.
2. **CI**: Checks compatibility between the current version and all patches of the current and previous minor.
3. **Full**: Checks all supported upgrade paths, i.e. upgrading from any patch to any other patch of the current or next minor.

### Full test coverage

Testing the full coverage is expensive because it tests more than 1.5k upgrade paths.
We run this test periodically through the [zeebe-version-compatibility.yml workflow].

In order to reduce the time and cost of the full test coverage, we run this test incrementally.
Each run reports a list of tests and version combinations that were tested successfully.
This list is saved as an artifact and used in the next run to skip the tests that were already successful.

#### FAQ

##### How do I check if a version combination was tested?

The full test coverage report is stored as an artifact of the last successful run of the [zeebe-version-compatibility.yml workflow].
You can download the `zeebe-version-compatibility` artifact and search for the version you are interested.
The specific version combination should appear multiple times, once for each test method.

##### How do I run the full test coverage locally?

Set the following environment variables:
- `ZEEBE_CI_CHECK_VERSION_COMPATIBILITY=true`
- `ZEEBE_CI_CHECK_VERSION_COMPATIBILITY_REPORT=~/zeebe-version-compatibility.csv`
Then run the `RollingUpdateTest`s: with `mvn verify -D it.test=io.camunda.zeebe.test.RollingUpdateTest`
If you keep the report file around, another run of this test will continue from where the previous run stopped and only test version combinations that weren't tested successfully yet.

##### I changed the `RollingUpdateTest`, do I need to do anything?

**Adding another test method** to `RollingUpdateTest` will automatically run all this test method for all supported version combinations.

**Changing an existing test method**, requires a reset of the stored coverage report to restart the incremental testing from scratch.
You can do this by navigating to the last successful test run of the [zeebe-version-compatibility.yml workflow] and deleting the `zeebe-version-compatibility` artifact.

##### How does the incremental testing work?

The `RollingUpdateTest` uses our own `CachedTestResultsExtension` JUnit extension.
This extension allows to cache the test results of the parameterized test methods by storing them in a file.
In the `zeebe-version-compatibility.yml` workflow merges the caches of all parallel test runs and stores the result as an artifact.
The next run of the `RollingUpdateTest` restores the cache from the artifact of the last successful run.
Then the `CachedTestResultsExtension` uses the cache to skip tests that already ran.

##### How can I change the parallelism of the full test coverage?

The parallelism is controlled by the `zeebe-version-compatibility.yml` workflow by using a matrix strategy.
You can change the parallelism by adding more entries to the `shards` input of the matrix strategy.
The actual values of the matrix input are not important, only the number of entries is relevant.
For each matrix job, the `RollingUpdateTest` will run with on a separate "shard" of the full set of version combinations.

You may want to increase parallelism after a reset of the coverage report to speed up the testing.
Once the report is complete, you can reduce the parallelism to save resources.

[zeebe-version-compatibility.yml workflow]: https://github.com/camunda/camunda/actions/workflows/zeebe-version-compatibility.yml

