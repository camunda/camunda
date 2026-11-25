# A transport-neutral cluster gossiper with GLOBAL and MEMBER scoped keys

**DRI**: Deepthi Devaki Akkoorath

**Status**: Proposed

**Purpose**: Defines the `ClusterGossiper` API — the contract components use to share small,
eventually consistent, in-memory state across all cluster members without knowing how it is
transported.

**Audience**: Contributors to Zeebe cluster components that publish or consume cluster-wide state
(topology, flags, membership metadata), and AI agents reasoning about how cluster state is shared.

## Context

Components share cluster state today by mutating the local SWIM member's properties in place
(`Member.properties()`, a `java.util.Properties`). `TopologyManagerImpl` publishes partition roles
and health this way, and `DefaultClusterEventService` publishes topic subscriptions.
`SwimMembershipProtocol` polls those properties for changes, bumps the member's incarnation number
and gossips the full property set. This has several problems:

- **Consumers are tied to SWIM.** Every consumer knows that its state lives in SWIM member
  properties, encodes it as strings, and parses other members' properties itself.
- **No ownership of keys.** Any component can write any property name; nothing prevents collisions
  or two components disagreeing on a value's format.
- **Removals do not propagate.** Remote members merge with `putAll`, so a property removed by its
  owner stays on every remote member until the owner leaves SWIM.
- **Only per-member state.** There is no way to share a value any member can write, which is why
  dynamic cluster configuration runs its own gossip protocol (`ClusterConfigurationGossiper`) with
  its own merge-by-version.
- **Size is bounded by UDP.** SWIM gossip goes over `NettyUnicastService` (UDP datagrams), so every
  property makes every member's SWIM update larger.

We want an abstraction any component can use to share state without knowing the transport, so that
the transport can be chosen and changed independently of consumers.

## Decision

**D1. Two scopes: GLOBAL and MEMBER.**
A *GLOBAL* key has one logical value cluster-wide. Any member may write it, and concurrent writes are
resolved by the key's merge strategy. A *MEMBER* key has one value per member; only that member
writes its own copy, so there are no concurrent writes, and readers see every member's copy. The
existing per-member properties are an instance of MEMBER scope; dynamic cluster configuration is an
instance of GLOBAL scope.

**D2. The API is transport-neutral.**
Nothing in the API refers to SWIM, properties, incarnation numbers or a particular wire channel.
Whether GLOBAL and MEMBER keys are carried on SWIM, on a separate anti-entropy channel over
`ClusterCommunicationService`, or a mix, is an implementation decision deferred to the
implementation. The API offers no guarantee that only one of these transports could keep, such as
synchronous acknowledgement of writes.

**D3. State is in-memory only.**
Nothing is persisted. A GLOBAL value survives as long as at least one live member still holds it, and
is lost when all members restart. Consumers that need durability persist the value themselves.

**D4. Keys are typed descriptors that carry their scope, codec and merge strategy.**
A key is a `GlobalKey<T>` or `MemberKey<T>` that names a cluster-wide unique key and carries a
`GossipCodec<T>` (`T <-> byte[]`). A `GlobalKey` also carries its `MergeStrategy<T>` and a default
value. Putting scope and merge on the key, rather than on each call, makes it impossible to write a
key as GLOBAL in one place and as MEMBER in another, or for two members to merge the same key
differently. Only fixed keys are supported; dynamic key families (`prefix.<suffix>`) are deferred.
A consumer that needs a dynamic set of entries puts a map inside one value.

**D5. Keys are registered on first use; conflicting descriptors fail fast.**
The gossiper starts early in the broker, before its consumers, so there is no registration phase
before start. A key is registered the first time it is used. Using the same name with an
incompatible descriptor (different scope, value type or merge kind) throws
`IllegalStateException`.

**D6. Codecs must be deterministic; format evolution is the consumer's concern.**
Equal values must encode to identical bytes. The gossiper detects changes by comparing encoded bytes,
which keeps change detection independent of `equals` on `T`. A codec that cannot decode a received
value (for example during a rolling update) causes a warning; local readers see the value as absent.
Versioning the encoded format is left to the consumer.

**D7. GLOBAL merges form a join-semilattice; two strategies are offered.**
A merge must be commutative, associative and idempotent, because updates arrive in any order, more
than once and along different paths. Two strategies exist:

- `lastWriterWins()` — the gossiper attaches a hidden `(timestamp, writer member id)` header; the
  highest timestamp wins, ties go to the higher member id, and remaining ties to the greater encoded
  value. The last tie-break keeps the order total when a restarted member reuses a timestamp of its
  previous run for a different value, which would otherwise leave members disagreeing. The timestamp
  is wall-clock time, so under clock skew the member with the faster clock wins. Because the header
  is hidden, it can move to a hybrid logical clock later without an API change.
- `merging(BinaryOperator<T>)` — merges values only. Any ordering the consumer needs, such as a
  version, lives inside `T`.

A third strategy exposing the gossiper's header to custom merges is deliberately not offered.

**D8. Local GLOBAL writes go through the merge.**
`put(key, value)` stores `merge(current, value)`; under last-writer-wins this replaces the value.
`update(key, fn)` applies `fn` to the current value and stores the merged result. Storing a value raw
under a custom merge would be overridden as soon as a peer gossips back, which looks like a lost
write.

**D9. MEMBER writes target the local member only.**
`put(MemberKey, value)` takes no member id, so the single-writer rule is enforced by the API rather
than by convention.

**D10. A MEMBER value lives for one run of its member.**
Readers never see a value written by an earlier run of a member. A value disappears when its member
leaves the cluster or restarts, and listeners are told with an empty value. SWIM already detects
restarts through the member's instance id and replaces the member; the contract requires any other
transport to give the same guarantee.

**D11. GLOBAL keys always have a value.**
A `GlobalKey` declares a default value, returned by `get` until a value is received. The default is
never gossiped. This removes the main reason for a consumer to write on startup — writing a default
while the local view is still empty would, under last-writer-wins, override the value the rest of the
cluster holds. `get(GlobalKey)` therefore returns `T`, while `get(MemberKey, MemberId)` returns
`Optional<T>`, because "this member has not written the key" is real information.

**D12. Writes are fire-and-forget.**
Writes are non-blocking, thread-safe, visible locally right away and propagated asynchronously. They
return nothing: gossip cannot report "reached every member", and "sent to one peer" is useless to a
caller.

**D13. Subscriptions are level-triggered, replay on subscribe and run on the caller's executor.**
A listener eventually sees the latest value, not necessarily every intermediate value. Subscribing
immediately delivers the current state, so there is no race between `get` and `subscribe`.
Callbacks run on an `Executor` the subscriber passes in, so consumer code need not run on, or stall,
the gossiper's own threads. The executor must not block; a direct executor runs callbacks on the
gossiper's thread. Local writes notify local listeners too.

**D14. The transport must re-deliver state eventually.**
Values received for a key that is not registered locally yet may be dropped. The implementation must
eventually re-deliver the current state of every key to every live member (anti-entropy), which also
covers members returning from a network partition.

**D15. Brokers and gateways take part as equals.**
Gateways are cluster members and may read and write both scopes.

**D16. The API lives in its own API-only module, with an in-memory test kit.**
`zeebe/cluster-gossip/api` contains only the contract and depends on nothing but `camunda-cluster`
(for `MemberId`). Implementations live in separate modules. `zeebe/cluster-gossip/testkit` provides
an in-memory, multi-member implementation with controllable delivery, partitions and restarts, so
consumers can test convergence without starting SWIM, plus helpers to check that a merge function
satisfies D7.

**D17. The first implementation carries MEMBER keys over SWIM; GLOBAL keys are not supported yet.**
`zeebe/cluster-gossip/swim` stores each MEMBER value as the property of its owner's SWIM member that
is named exactly like the key, holding the value in Base64. That is the format components already use
when they write member properties directly (for example `brokerInfo`), so they can move to the
gossiper without changing what other members, including older versions during a rolling update,
read. In return, the gossiper owns no namespace: on any one member, a property must be written either
through the gossiper or directly, never both; members on different versions may differ. Every GLOBAL operation throws `UnsupportedOperationException`. The
current use cases only need MEMBER keys, and SWIM already provides their lifetime (D10): SWIM drops a
member's properties when it leaves or restarts. This makes nothing worse than today — components
already gossip through SWIM properties, and would keep writing them directly if there were no
implementation — while putting all such state behind one API, so moving to another transport later
changes one module instead of every consumer.

A dedicated transport, using SWIM only for membership, is the preferred long-term direction and will
be evaluated before GLOBAL keys are implemented. A proof of concept on localhost showed why GLOBAL
keys should not go on SWIM: every member must republish a GLOBAL value for it to outlive its writer,
and every SWIM probe resends the full properties of both members involved, so in a five-member
cluster each byte of GLOBAL state cost about 14 bytes per second of background traffic, against
about 0.5 for a dedicated anti-entropy transport. SWIM's UDP gossip also drops a member's update
silently once its properties exceed one datagram (about 64 KB); probes and syncs over TCP still
deliver it, but more slowly.

## Consequences

- Consumers depend on a small contract and can be tested against the in-memory test kit.
- The transport can be chosen, and later changed, without touching consumers.
- Until GLOBAL keys are implemented, consumers can only use MEMBER keys in production; the test kit
  implements both scopes.
- MEMBER values add to the size of every SWIM update of their member, so they must stay small.
- Last-writer-wins on wall-clock time is sensitive to clock skew. This is accepted for now.
- Large values are sent whole on every change because there are no key families. This is accepted
  until a consumer needs more.

## Deferred

- **A dedicated transport and GLOBAL keys** (D17).
- **Deletes.** There is no `remove` in the first version, for either scope. Consumers that want to
  clear a value write an empty value of their own type. Because reads already return `Optional` or a
  default, adding deletes (with tombstones for GLOBAL keys) later is backwards compatible.
- **Dynamic key families.**
- **Hybrid logical clock** for last-writer-wins.
- **Size limits.** These are an implementation limit; implementations may reject or fail oversized
  values, so consumers should keep values small.
- **A readiness signal** for the first successful sync; defaults (D11) are enough for now.
- **Migrating existing consumers** of member properties (`TopologyManagerImpl`,
  `DefaultClusterEventService`) and `ClusterConfigurationGossiper`. Because the SWIM implementation
  keeps the existing property format (D17), moving a consumer whose property names are fixed is a
  refactor that older members cannot tell apart. Gateways, however, discover partition groups by
  scanning every `brokerInfo:<group>` property, which is a key family; migrating them needs either
  the group names from the cluster configuration or key families in the API.

## Alternatives considered

- **Strings or raw bytes keyed by plain strings.** Simplest, and closest to today, but every consumer
  re-implements serialization and nothing prevents name collisions or scope confusion.
- **Explicit registration before the gossiper starts.** Does not fit a broker where the gossiper
  starts before its consumers; replaced by registration on first use (D5).
- **Keeping and forwarding every distinct value of a not-yet-registered custom-merge key.** Loses
  nothing, but is complex and unbounded. Re-delivery (D14) is needed for partitioned members anyway,
  so dropping such values costs only a short delay.
- **A `CompletableFuture` returned from writes.** There is no completion that gossip can honestly
  report (D12).
- **Readiness signal instead of defaults.** Deferred; defaults remove the most common misuse with
  less API.

