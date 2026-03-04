```yaml
---
applyTo: "zeebe/atomix/cluster/**"
---
```
# Atomix Cluster Module

## Purpose

This module is a hard fork of the Atomix framework providing cluster formation, failure detection, and inter-node messaging for the Zeebe distributed process engine. It implements SWIM-based membership protocol and Netty-based TCP/UDP messaging, forming the communication substrate on which Raft consensus and all broker-to-broker interactions are built.

## Architecture

The module is organized into four packages, each with a clear responsibility:

### Package: `io.atomix.cluster` (root)
Core abstractions for cluster identity and lifecycle. `AtomixCluster` is the top-level orchestrator that wires together all subsystems and manages their startup/shutdown order.

- `AtomixCluster` — main entry point, implements `BootstrapService` and `Managed<Void>`. Starts services in order: messaging → unicast → membership → communication → event. Shuts down in reverse. See `src/main/java/io/atomix/cluster/AtomixCluster.java`.
- `AtomixClusterBuilder` — fluent builder configuring `ClusterConfig` (cluster ID, member ID, address, TLS, compression, discovery provider, membership protocol).
- `Node` / `Member` — identity hierarchy. `Node` has `NodeId` + `Address`. `Member` extends `Node` with `MemberId`, zone/rack/host topology, properties, and `nodeVersion` (for distinguishing incarnations in dynamic environments like AWS ECS).
- `BootstrapService` — interface exposing `MessagingService` + `UnicastService` for low-level bootstrapping.
- `ClusterMembershipService` — high-level membership view: local member, member set, member lookup by ID or address. Extends `ListenerService` for `ClusterMembershipEvent` notifications.

### Package: `io.atomix.cluster.protocol`
Membership protocol abstraction and the SWIM implementation.

- `GroupMembershipProtocol` — SPI interface for membership protocols. Uses `Type` pattern for factory-based instantiation from config. Methods: `join()`, `leave()`, `getMembers()`, `getMember()`.
- `SwimMembershipProtocol` — SWIM protocol implementation (~900 lines). Manages three periodic tasks: gossip (disseminating member state), probe (failure detection via direct + indirect pings), and sync (full state exchange). Uses `MessagingService` for probe/sync (TCP) and `UnicastService` for gossip (UDP). Configurable via `SwimMembershipProtocolConfig` (gossip interval 250ms, probe interval 1s, probe timeout 2s, failure timeout 10s, sync interval 10s by default).
- `DiscoveryMembershipProtocol` (in `impl/`) — simpler protocol that derives membership directly from the `NodeDiscoveryService` events. No gossip or probing. Used for testing and simpler topologies.
- Member states flow: `ALIVE → SUSPECT → DEAD`. Incarnation numbers prevent stale state from being applied.

### Package: `io.atomix.cluster.discovery`
Node discovery SPI for finding cluster peers.

- `NodeDiscoveryProvider` — SPI interface with `Type` factory pattern. Methods: `join()`, `leave()`, `getNodes()`.
- `BootstrapDiscoveryProvider` — static list of known nodes. Used when cluster topology is predefined.
- `DynamicDiscoveryProvider` — DNS-based discovery that periodically resolves hostnames to IP addresses. Fires `JOIN`/`LEAVE` events as DNS records change. Configurable refresh interval and default port.
- `DefaultNodeDiscoveryService` — bridge between a `NodeDiscoveryProvider` and the membership service, forwarding discovery events.

### Package: `io.atomix.cluster.messaging`
Three-layer messaging stack, all Netty-based.

**Layer 1 — Transport (`MessagingService` / `UnicastService`)**:
- `NettyMessagingService` — reliable TCP messaging. Manages Netty server/client bootstraps, `ChannelPool` for connection reuse, TLS (TLSv1.3), optional compression (Snappy/GZIP), heartbeat keep-alive, and DNS resolution. Wire protocol uses `ProtocolRequest`/`ProtocolReply` with two versions (`MessagingProtocolV1`, `MessagingProtocolV2`). Supports `sendAsync` (fire-and-forget) and `sendAndReceive` (request-reply with timeout).
- `NettyUnicastService` — unreliable UDP messaging for gossip dissemination.
- `HeartbeatHandler` — sealed class with `Client`/`Server` inner classes. Client sends heartbeats on write-idle, server replies. Either side closes connection on read-idle timeout. Uses SBE-encoded heartbeat payloads.

**Layer 2 — Cluster Communication (`ClusterCommunicationService`)**:
- `DefaultClusterCommunicationService` — translates `MemberId`-based messaging into `Address`-based calls via the membership service. Provides `broadcast`, `multicast`, `unicast`, `send` (request-reply), `consume`, and `replyTo` patterns. Resolves member addresses through `ClusterMembershipService.getMember()`.

**Layer 3 — Event Service (`ClusterEventService`)**:
- `DefaultClusterEventService` — publish-subscribe messaging with topic-based subscriptions. Tracks remote subscribers via member properties. Supports `broadcast`, `unicast`, `send`, and `subscribe` with custom encoders/decoders.

## Key Abstractions

| Type | Role |
|------|------|
| `AtomixCluster` | Top-level lifecycle manager; wires all services |
| `ClusterMembershipService` | Query cluster membership, listen for membership changes |
| `GroupMembershipProtocol` | Pluggable membership/failure detection (SWIM or discovery-based) |
| `NodeDiscoveryProvider` | Pluggable peer discovery (static bootstrap or DNS-based) |
| `MessagingService` | Low-level reliable TCP send/receive by `Address` |
| `UnicastService` | Low-level unreliable UDP send by `Address` |
| `ClusterCommunicationService` | High-level unicast/multicast/broadcast by `MemberId` |
| `ClusterEventService` | Pub-sub messaging by topic |
| `ProtocolMessage` / `ProtocolRequest` / `ProtocolReply` | Wire protocol message types |

## Data Flow

1. On startup, `AtomixCluster.start()` initializes Netty channels → discovery service joins → SWIM protocol registers handlers and begins gossip/probe/sync cycles.
2. For outbound messages: caller uses `ClusterCommunicationService.send(subject, message, memberId)` → service resolves `MemberId` to `Address` via `ClusterMembershipService` → delegates to `MessagingService.sendAndReceive(address, subject, payload)` → Netty encodes `ProtocolRequest` and writes to channel.
3. For inbound messages: Netty decodes `ProtocolRequest` → `HandlerRegistry` dispatches by subject → registered handler processes and optionally returns `ProtocolReply`.
4. SWIM protocol runs three concurrent cycles: gossip (UDP broadcast of member state changes), probe (TCP ping with indirect probe fallback), sync (full membership state exchange with a random peer).

## Extension Points

- **New membership protocol**: Implement `GroupMembershipProtocol` and `GroupMembershipProtocolConfig` with a `Type` factory. Register via `ClusterConfig.setProtocolConfig()`.
- **New discovery provider**: Implement `NodeDiscoveryProvider` and `NodeDiscoveryConfig` with a `Type` factory. Register via `ClusterConfig.setDiscoveryConfig()`.
- **New message handler**: Register via `MessagingService.registerHandler(subject, handler)` for low-level or `ClusterCommunicationService.consume/replyTo(subject, ...)` for high-level.

## Invariants

- Service startup order must be: messaging → unicast → membership → communication → event. Shutdown is the reverse. Violating this causes `NullPointerException` or lost messages.
- `ProtocolReply.Status` IDs are fixed for backward compatibility — never change existing enum constant IDs.
- `HeartbeatHandler.HEARTBEAT_SUBJECT` ("internal-heartbeat") must not change — it is part of the wire protocol.
- SWIM incarnation numbers must be strictly increasing; stale updates (lower incarnation) are rejected.
- All `Managed` services use `AtomicBoolean` guards to ensure `start()`/`stop()` are idempotent.
- `ClusterCommunicationService` silently drops messages to unknown members (returns `null` on `getMember()`). For request-reply, it fails with `NoSuchMemberException`.

## Common Pitfalls

- **Connection pooling**: `ChannelPool` pools connections by `(Address, InetAddress, messageType)` tuple. Changing connection semantics without understanding this key may cause connection leaks.
- **Serializer registration order**: SWIM's `SERIALIZER` uses positional `Namespace` registration. Adding or reordering types breaks deserialization across cluster versions.
- **Thread model**: SWIM uses dedicated `swimScheduler` (single-thread) and `eventExecutor` (single-thread). Do not perform blocking operations in membership event handlers.
- **TLS configuration**: Both `certificateChain`+`privateKey` (PEM) and `keyStore` (PKCS12/JKS) paths are supported, but they are mutually exclusive. `configureTls()` selects one based on which is non-null.
- **Protocol version negotiation**: The latest `ProtocolVersion` is used by default. Mixed-version clusters must ensure backward compatibility of encoders/decoders.

## Metrics

- `SwimMembershipProtocolMetrics` — tracks per-member incarnation numbers as Micrometer gauges.
- `MessagingMetrics` — tracks request timers, request sizes, message counts, response success/failure, and in-flight requests.
- `NettyDnsMetrics` — tracks DNS resolution operations.

## Testing

- Test files are in `src/test/java/io/atomix/cluster/`.
- `TestMessagingService`/`TestMessagingServiceFactory` — in-memory messaging for unit tests.
- `TestUnicastService`/`TestUnicastServiceFactory` — in-memory unicast for unit tests.
- `TestBootstrapService` / `TestDiscoveryProvider` — test doubles for bootstrap and discovery.
- `AtomixClusterRule` — JUnit rule for setting up multi-node cluster tests.
- Run tests: `./mvnw -pl zeebe/atomix/cluster -am test -DskipITs -DskipChecks -T1C`

## Key Reference Files

- `src/main/java/io/atomix/cluster/AtomixCluster.java` — lifecycle orchestrator and builder entry
- `src/main/java/io/atomix/cluster/protocol/SwimMembershipProtocol.java` — SWIM implementation (~900 lines)
- `src/main/java/io/atomix/cluster/messaging/impl/NettyMessagingService.java` — Netty TCP transport
- `src/main/java/io/atomix/cluster/messaging/impl/DefaultClusterCommunicationService.java` — MemberId-based messaging
- `src/main/java/io/atomix/cluster/messaging/impl/HeartbeatHandler.java` — connection keep-alive protocol