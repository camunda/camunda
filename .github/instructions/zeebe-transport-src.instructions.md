```yaml
---
applyTo: "zeebe/transport/src/**"
---
```
# Zeebe Transport Module

## Purpose

The `zeebe/transport` module provides asynchronous request-response and unidirectional push-based streaming communication over TCP/IP between Zeebe cluster nodes (brokers and gateways). It wraps the Atomix `MessagingService` and `ClusterCommunicationService` to provide two communication paradigms: partition-scoped request-response (used for client commands) and type-based remote streaming (used for job push from broker to gateway).

## Architecture

The module has two distinct subsystems, each with a clean API/impl split:

### 1. Request-Response Transport (`io.camunda.zeebe.transport`)
- **API interfaces**: `ClientTransport`, `ServerTransport`, `ClientRequest`, `ServerResponse`, `ServerOutput`, `RequestHandler`
- **Impl**: `AtomixClientTransportAdapter` (client), `AtomixServerTransport` (server), `ServerResponseImpl`, `RequestContext`
- Uses Atomix `MessagingService` point-to-point messaging with topic-based routing per partition and request type
- Topics follow format `<requestType>-api-<partitionId>` (legacy) or `<prefix>-<requestType>-api-<partitionId>`

### 2. Remote Streaming (`io.camunda.zeebe.transport.stream`)
- **API**: `stream/api/` — `ClientStreamer`, `ClientStreamService`, `RemoteStreamer`, `RemoteStreamService`, `RemoteStream`, `ClientStream`
- **Impl**: `stream/impl/` — managers, registries, pushers, API handlers, request managers
- **Messages**: `stream/impl/messages/` — SBE-encoded wire protocol defined in `stream-protocol.xml`
- Uses Atomix `ClusterCommunicationService` for cluster-wide pub/sub with dedicated stream topics (ADD, REMOVE, PUSH, REMOVE_ALL, RESTART_STREAMS)

### Factory
`TransportFactory` is the single entry point. It creates all transport instances and submits them as actors to `ActorSchedulingService`.

## Key Abstractions

| Type | Role |
|------|------|
| `ClientTransport` | Sends requests with optional retry and response validation |
| `ServerTransport` | Receives requests per partition/type, dispatches to `RequestHandler` |
| `RequestType` | Enum: `COMMAND`, `QUERY`, `ADMIN`, `BACKUP`, `SNAPSHOT`, `UNKNOWN` |
| `TopicSupplier` | Maps `(partitionId, RequestType)` to a messaging topic string |
| `RemoteStreamer<M,P>` | Server-side: finds a stream by type/filter and pushes payload to a gateway |
| `ClientStreamer<M>` | Client-side: registers streams with servers, receives pushed payloads |
| `AggregatedRemoteStream<M>` | Groups multiple consumers (gateways) with same `LogicalId` (streamType + metadata) |
| `AggregatedClientStream<M>` | Groups logically equivalent local client streams under one server-registered UUID |
| `ClientStreamRegistration` | State machine (INITIAL→ADDING→ADDED→REMOVING→REMOVED→CLOSED) tracking per-server registration |
| `RemoteStreamRegistry<M>` | Thread-safe registry mapping stream types to aggregated consumer sets |

## Data Flow

### Request-Response
1. `ClientTransport.sendRequestWithRetry()` copies request bytes, resolves node address via supplier, sends via `MessagingService.sendAndReceive()`
2. On timeout or connection error, retries after 10ms delay until overall timeout expires
3. `ServerTransport.subscribe()` registers `MessagingService` handler for topic; dispatches to `RequestHandler.onRequest()`
4. Handler processes request, calls `ServerOutput.sendResponse()` which completes the `CompletableFuture` held in `partitionsRequestMap`

### Remote Streaming (Broker→Gateway push)
1. **Registration**: Client calls `ClientStreamer.add()` → `ClientStreamManager` creates `AggregatedClientStream`, `ClientStreamRequestManager` sends `AddStreamRequest` to all known servers via `stream-add` topic
2. **Push (server side)**: Engine calls `RemoteStreamer.streamFor()` → `RemoteStreamImpl.push()` → `RemoteStreamPusher` serializes `PushStreamRequest`, sends via `stream-push` topic. On failure, retries with shuffled consumers.
3. **Push (client side)**: `ClientStreamApiHandler` receives push → `ClientStreamManager.onPayloadReceived()` → `ClientStreamPusher` tries each local `ClientStreamConsumer` until one succeeds or all are exhausted.
4. **Membership**: `RemoteStreamService` listens for cluster membership events; on MEMBER_REMOVED, clears all streams for that node; on MEMBER_ADDED, sends RESTART_STREAMS request.

## Wire Protocol

Messages are SBE-encoded, defined in `src/main/resources/stream-protocol.xml` (schema ID 2, version 2). Message IDs 400–406:
- `AddStreamRequest` (400), `AddStreamResponse` (403)
- `RemoveStreamRequest` (401), `RemoveStreamResponse` (404)
- `PushStreamRequest` (402), `PushStreamResponse` (405)
- `ErrorResponse` (406) with `ErrorCode` enum: INTERNAL, NOT_FOUND, INVALID, MALFORMED, EXHAUSTED, BLOCKED

SBE codecs are generated at build time via `exec-maven-plugin` from the XML schema. Never edit generated encoder/decoder classes directly.

## Extension Points

- **New `RequestType`**: Add enum value in `RequestType.java`; subscribe to it in the server transport consumer
- **New stream topic**: Add value to `StreamTopics` enum; register handler in `RemoteStreamTransport.onActorStarting()` or `ClientStreamServiceImpl.onActorStarted()`
- **New SBE message**: Add `<sbe:message>` to `stream-protocol.xml`, implement corresponding `BufferReader`/`BufferWriter` wrapper class in `messages/`

## Concurrency Model

- All transport classes extend `Actor` (cooperative scheduling via `zeebe/scheduler`). State mutations happen on the actor thread — no locks needed.
- `RemoteStreamRegistry` uses `ConcurrentHashMap` and `CopyOnWriteArraySet` for thread-safe reads from the `RemoteStreamerImpl` actor.
- `ClientStreamRequestManager` is NOT thread-safe; must run on the same actor thread as `ClientStreamManager`.
- `RemoteStreamPusher` uses an `Executor` (the actor's `run` method) for async push callbacks.

## Invariants

- Always create transports via `TransportFactory` — it submits actors to the scheduling service.
- `RequestContext` copies request bytes once at creation; the original `ClientRequest` buffer is not retained.
- `ServerResponseImpl` allocates a fresh byte array per response because `sendResponse` can be called concurrently from different partitions.
- `ClientStreamRegistration` state transitions are strictly ordered; violating the state machine logs a trace message and returns false.
- Payloads pushed via `RemoteStream.push()` must be immutable — push is asynchronous and may be retried across different consumers.

## Common Pitfalls

- Adding a new `RequestType` without registering topic handlers on both client and server will cause `NoRemoteHandler` exceptions.
- Modifying generated SBE classes (e.g. `AddStreamRequestEncoder`) directly — they are overwritten on every build. Edit `stream-protocol.xml` instead.
- Sharing mutable state between the `RemoteStreamRegistry` (read by streamer actor) and `RemoteStreamApiHandler` (mutated by transport actor) — the registry's collections are already thread-safe by design; do not replace them with non-concurrent alternatives.
- Forgetting to handle `ClientStreamBlockedException` — it is a normal flow control signal, not a fatal error.

## Key Files

| File | Purpose |
|------|---------|
| `main/.../TransportFactory.java` | Factory creating all client/server/stream transports |
| `main/.../impl/AtomixClientTransportAdapter.java` | Client-side request-response with retry logic |
| `main/.../impl/AtomixServerTransport.java` | Server-side request routing and response dispatch |
| `main/.../stream/impl/RemoteStreamerImpl.java` | Server-side stream lookup and push orchestration |
| `main/.../stream/impl/ClientStreamServiceImpl.java` | Client-side stream lifecycle and push reception |
| `main/.../stream/impl/ClientStreamRequestManager.java` | Registration state machine for client→server stream setup |
| `main/.../stream/impl/RemoteStreamRegistry.java` | Thread-safe aggregated stream registry |
| `main/resources/stream-protocol.xml` | SBE schema defining all stream wire messages |

## Dependencies

- `zeebe-atomix-cluster` — `MessagingService`, `ClusterCommunicationService`, `MemberId`
- `zeebe-scheduler` — `Actor`, `ActorFuture`, `ActorSchedulingService`, `ConcurrencyControl`
- `zeebe-util` — `BufferWriter`/`BufferReader`, `Either`, `ExponentialBackoff`, `SbeUtil`
- `agrona` — `DirectBuffer`, `UnsafeBuffer`, `IdGenerator`, off-heap collections

## Consumers

This module is depended on by `zeebe/broker`, `zeebe/gateway-grpc`, `zeebe/broker-client`, `zeebe/backup`, and various QA/test modules.