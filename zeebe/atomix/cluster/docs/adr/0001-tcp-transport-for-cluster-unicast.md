# No-UDP cluster communication: select the unicast transport at the `UnicastService` seam

**DRI**: Nicolas Pepin-Perreault

**Status**: Proposed

**Purpose**: Defines how a Camunda cluster can run without UDP — which component chooses the
transport for unreliable unicast, what the TCP path puts on the wire, and what operators must
guarantee before enabling it.

**Audience**: Engineers working on `zeebe/atomix/cluster` (messaging, membership), on cluster
configuration, and AI agents changing the unicast or SWIM code paths.

## Context

Zeebe uses UDP for exactly one thing: SWIM membership gossip. `SwimMembershipProtocol` sends
gossip and dispute broadcasts through `UnicastService.unicast(...)` under the single subject
`atomix-membership-gossip`, and the only implementation is `NettyUnicastService`, which binds a
datagram socket. There is no way to turn it off.

That blocks two kinds of users:

- **Encryption mandates.** UDP has no DTLS equivalent here, so membership gossip is always in the
  clear — even in a cluster with TLS enabled on the internal API.
- **Networks where UDP is not routable** (reverse proxies, service meshes, network policies). The
  cluster still forms, because SWIM's probe and sync are TCP, so the misconfiguration is invisible
  until it surfaces as slow convergence and odd failure detection.

Four properties of the existing code shape the decision:

1. **UDP has one user in practice.** Besides SWIM gossip, the only other path into
   `UnicastService` is `DefaultClusterCommunicationService.doUnicast` when `reliable == false`.
   Every in-repo caller of `ClusterCommunicationService.unicast` passes `reliable = true`
   (`ClusterConfigurationGossiper`, `InterPartitionCommandSenderImpl`,
   `ClientStreamRequestManager`), so no production traffic other than membership gossip is at stake.
2. **Subjects are a single shared namespace with silent overwrite.** `HandlerRegistry.register` is a
   `map.put`, and `DefaultClusterCommunicationService.consume()` registers a *messaging handler* and a
   *unicast listener* under the same subject string. A TCP-based unicast service that registered a
   messaging handler on the bare subject would silently clobber the `consume()` handler.
3. **The messaging service already carries the sender's advertised address.**
   `NettyMessagingService.sendAsync` builds `new ProtocolRequest(id, advertisedAddress, type,
   payload)`, and `registerHandler(type, BiConsumer<Address, byte[]>, executor)` dispatches
   `handler.accept(message.sender(), message.payload())`. This is the same address
   `NettyUnicastService` puts in its own `Message` envelope, so a TCP path needs no envelope of its
   own and `membershipService.getMember(sender)` keeps resolving.
4. **Lifecycle ordering already permits it.** `AtomixCluster.startServices()` starts the messaging
   service *before* the unicast service and `stopServices()` stops them in the reverse order, so a
   unicast implementation may register handlers on the messaging service during `start()`.

## Decision

**0. Transports are owned, and lifecycled, by one service.** A preparatory refactor grouped a node's
messaging and unicast primitives behind `NetworkService` — a facade with two accessors — and
`ManagedNetworkService extends NetworkService, Managed<NetworkService>`, implemented by
`NettyNetworkService`, which owns both transports and encapsulates their start/stop ordering.
`AtomixCluster` holds one managed service instead of two.

Three properties of that arrangement are load-bearing here:

- **Composition, not multiple inheritance.** `Managed<T>.start()` returns `CompletableFuture<T>`, so
  no class can be both a `ManagedMessagingService` and a `ManagedUnicastService`. Grouping the
  transports makes that constraint irrelevant instead of something to route around, which is what
  lets decision 1 below add unicast to the messaging service at all.
- **The facade hands out unmanaged views.** `messagingService()` and `unicastService()` return the
  plain interfaces, so a consumer cannot stop a transport it shares. Once one primitive is carried by
  the other's transport, a caller stopping "its" service would take out the other's.
- **Lifecycle follows the owner.** Anything that merely *routes* to a transport is not `Managed` —
  which is why the router in decision 3 has no lifecycle of its own.

**1. `NettyMessagingService` also implements `UnicastService`.** Unreliable unicast over TCP is
`sendAsync` plus a handler, so the class that owns the TCP transport owns those semantics too. It
implements the plain, unmanaged `UnicastService` alongside `ManagedMessagingService` — legal, because
only one `Managed<…>` parameterization is involved, and collision-free, because the two interfaces'
methods are disjoint (`address/bindingAddresses/sendAsync/sendAndReceive/registerHandler/
unregisterHandler/isRunning` versus `unicast/addListener/removeListener`). The lifecycle still
belongs to the `ManagedNetworkService` that owns this instance.

The decisive reason to put it here rather than in a standalone TCP unicast class: it allocates **no
new Netty resources**. Name resolution, event loop groups, the connection pool, TLS, compression and
heartbeats are already this class's, and unicast reuses all of them.

The broker's command API builds its own `NettyMessagingService` instance
(`ApiMessagingServiceStep`) and therefore inherits an unused unicast capability. It is inert: nothing
registers listeners on it, so a stray `atomix-unicast:*` message finds no handler.

**2. Transport selection happens in one stateless router**, `CompositeUnicastService`, constructed
inside `NettyNetworkService` from `MessagingConfig.isUdpEnabled()` and returned from
`NetworkService.unicastService()`. It holds a *primary* delegate for sending and a list of
*receivers* for listener fan-out; both delegate kinds are plain `UnicastService`, so one class covers
both jobs:

|  `udp-enabled`   |       primary (sends)       |            receivers (listen)             |
|------------------|-----------------------------|-------------------------------------------|
| `true` (default) | `NettyUnicastService` (UDP) | `NettyUnicastService` + messaging service |
| `false`          | messaging service (TCP)     | messaging service                         |

Because the router is built and held inside the network service, the flag never reaches
`AtomixCluster`: transport composition stays an implementation detail of one class.
The router can therefore be package-private.

**3. The router is stateless — deliberately not a `ManagedUnicastService`.** Per decision 0, only
owners of resources are `Managed`, and the router owns none: it routes to transports whose lifecycle belongs to
the enclosing `ManagedNetworkService`. Had it implemented `Managed`, its reported state could disagree
with reality in both directions — `stop()` would not stop TCP delivery, since the registration lives
in the messaging service's `HandlerRegistry`; and stopping the messaging service would kill TCP
unicast while the router still reported `isRunning() == true`, then silently revive it on restart,
because `HandlerRegistry` is a `final` field that `stop()` never clears.

The consequence to accept: "TCP unicast is available exactly when the messaging service is running"
is now a structural fact rather than something the router asserts. Callers —
`SwimMembershipProtocol`, `DefaultClusterCommunicationService` — are unchanged and remain
transport-unaware.

With UDP disabled, `NettyUnicastService` is never constructed, so **no datagram socket is bound at
all**. Receiving gossip in the clear is as disqualifying as sending it, so "don't send" is not
sufficient.

**4. The TCP path owns a prefixed subject namespace.** `NettyMessagingService.unicast` sends, and
`addListener` registers, under `"atomix-unicast:" + subject` — never the bare subject. This is forced
by context (2): the prefix is what keeps unicast traffic from colliding with a `consume()`/`replyTo()`
handler registered on the same subject. Both the prefixing and the registration live in that one
class, so the prefix has a single owner.

> **The prefix is a wire contract.** Both peers must derive the same subject string, so it cannot be
> changed, made configurable, or "simplified away" without breaking rolling upgrades.

**5. TCP unicast preserves fire-and-forget semantics.** `unicast(...)` returns `void`, so the
`sendAsync` future's failure is swallowed at `debug` level. Gossip to an unreachable peer must not
propagate an error to SWIM, and must not log-flood at the gossip interval (250 ms by default).

**6. In UDP mode a node sends over UDP but receives on both transports** — the asymmetry in the table
above. It is deliberate: it is what lets the flag be flipped one node at a time, because a node that
has already flipped can be heard by a node that has not.

**7. The effective mode is logged once at startup**, so an operator can confirm from the logs which
transport gossip is using.

**8. The switch is a config property**, `camunda.cluster.network.udp-enabled`, default `true` — no
behaviour change for existing deployments. It lives under `camunda.cluster.network` because that is
what maps onto the two objects that build `MessagingConfig`: the broker's `NetworkCfg`
(`ClusterConfigFactory`) and the standalone gateway's `ClusterCfg` (`GatewayBasedConfiguration`).

## Alternatives considered

- **Branch inside `NettyUnicastService`.** Rejected: it would leave the class owning two transports
  and a socket it conditionally binds, and every future reader would have to re-derive which mode a
  given line belongs to. Choosing an implementation keeps each class single-transport and confines
  the decision to one factory branch.
- **Reuse the bare subject on the TCP path.** Rejected: silently overwrites `consume()`-registered
  handlers (context 2). The failure would be a lost message with no error, in a component whose whole
  job is detecting lost messages.
- **DTLS for the UDP path.** Rejected: it addresses only the encryption half of the problem, not the
  routability half, and adds a second TLS configuration surface (keys, ciphers, handshake tuning) for
  one subject's worth of traffic. Reusing the already-TLS-capable internal API gets both for free.
- **A standalone TCP unicast class delegating to the messaging service** (a `TcpUnicastService`
  implementing `ManagedUnicastService`), leaving `NettyMessagingService` untouched. Also correct, and
  it would allow unit-testing unicast against a stubbed `MessagingService` with no sockets. Rejected
  because it needs *two* new classes — the adapter plus the composite — where making
  `NettyMessagingService` a `UnicastService` needs one: the delegates then share a single type, so one
  adapter covers both send-selection and receive fan-out. The subject prefix also ends up split from
  the transport that implements it.
- **Hand-roll a TCP transport inside the unicast service.** Rejected: `NettyUnicastService` already
  duplicates `NettyMessagingService`'s DNS resolver setup, event-loop creation and graceful-shutdown
  handling near-verbatim (and, as a result, missed its Epoll transport selection). A second
  hand-rolled TCP path would duplicate name resolution, connection pooling, TLS, compression and
  heartbeats on top of that. Delegating to `sendAsync` inherits all of it.
- **Make gossip reliable and delete unreliable unicast entirely.** Rejected as the default: it
  changes failure and load characteristics for every existing deployment. The flag lets operators who
  need it opt in, and leaves that consolidation available later if the TCP path proves itself.
- **Put the flag under `camunda.cluster.membership`.** Semantically tighter, since gossip is the only
  UDP user, but `camunda.cluster.membership.*` feeds `SwimMembershipProtocolConfig`, not
  `MessagingConfig`. It would mean threading `MembershipCfg` into both messaging builders for no
  functional gain.

On decision 0 (transport ownership), three further options were weighed and rejected:

- **Leave the two transports as separate managed services in `AtomixCluster`**, with the UDP one held
  in a nullable field or a 0-or-1 element list. Rejected: it keeps the start/stop ordering as
  knowledge in `AtomixCluster` and splits ownership across two fields, which is what allows a
  partial-lifecycle bug in the first place.
- **A lifecycle listener**, so a routing service reacts to its backing transport stopping. Rejected:
  a new observer mechanism for a single consumer, requiring symmetric start *and* stop callbacks to
  avoid a service that stops once and never returns — and it papers over the inconsistency instead of
  removing it.
- **Derive `isRunning()` on the router from its delegates**, keeping it `Managed`. Rejected: it fixes
  the stale-state symptom only. A `stop()` that cannot actually stop what it routes to stays
  misleading, so the state is better deleted than corrected.

## Consequences

**Enables**

- A cluster whose entire inter-node communication runs over the internal API, and is therefore
  encrypted whenever internal-API TLS is on.
- Deployments behind proxies and service meshes that never routed UDP; the silent-degradation failure
  mode disappears because there is no UDP path to misconfigure.

**Constraints this creates**

- **All nodes must carry the TCP listener before the flag is enabled anywhere.** A flipped node
  gossiping to a node running a pre-feature version hits `ERROR_NO_HANDLER`
  (`AbstractServerConnection.dispatch`) and the gossip is silently dropped. Enabling the flag during a
  version rollout is therefore not safe; upgrade first, flip second.
- **The prefix and the composite's receive-on-both behaviour must both be preserved** for
  one-node-at-a-time flips to keep working. Removing either turns a rolling flip into a gossip
  blackout in one direction.

**Costs**

- UDP gossip is one fire-and-forget datagram. TCP gossip reuses a pooled connection to a live peer,
  which is cheap, but for an *unreachable* peer each gossip interval triggers a connection attempt
  instead of a datagram write. Failure detection itself still rests on TCP probes, so this is a cost
  question, not a correctness one.
- One extra class in the UDP-enabled path (`CompositeUnicastService`) and one indirection on the
  receive side.

**Scope of the guarantee**

The flag governs Camunda's own cluster protocols. Standard infrastructure protocols the JVM speaks
underneath — DNS above all — still use UDP on ephemeral client ports and are unaffected. The claim is
"no cluster communication over UDP", not "this process emits no UDP".

**Known collateral**

`ClusteringRule.disconnect`/`connect` (`zeebe/qa/integration-tests`) casts
`cluster.getUnicastService()` to `NettyUnicastService` to close the datagram socket. The router is
not one. Rather than expose an internal service to keep a legacy test rule working, `disconnect`/
`connect` drop the unicast leg and stop/start the messaging and API messaging services only — Raft
replication and SWIM probe/sync are all TCP, so that is what isolates a node. `FailOverReplicationTest`
and `BackupErrorResponseTest` are the only callers and must be re-run to confirm; migrating them to
`TestStandaloneBroker` is tracked separately.

## Source

- Issue: [camunda/camunda#61663](https://github.com/camunda/camunda/issues/61663)
- Discussion: https://camunda.slack.com/archives/C08MRKHJ0CD/p1785845950877019

