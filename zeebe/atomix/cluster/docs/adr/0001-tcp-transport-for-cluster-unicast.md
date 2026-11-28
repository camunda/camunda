# No-UDP cluster communication: send membership gossip over TCP

**DRI**: Nicolas Pepin-Perreault

**Status**: Proposed

**Purpose**: Defines how a Camunda cluster can run without UDP, and what operators must do before
they disable it.

**Audience**: Engineers and AI agents changing the messaging, unicast, or SWIM code in
`zeebe/atomix/cluster`.

## Context

UDP is used for one thing only: SWIM membership gossip. `SwimMembershipProtocol` sends gossip
through `UnicastService.unicast(...)`, and the only implementation, `NettyUnicastService`, binds a
UDP socket on the internal API port. There is no way to turn it off. This is a problem for two
kinds of users:

- **Users who must encrypt all traffic.** UDP gossip is never encrypted, even when TLS is enabled on
  the internal API.
- **Users whose network does not route UDP** (proxies, service meshes, network policies). The
  cluster still forms, because SWIM probes and syncs use TCP. The problem stays hidden until it
  shows up as slow convergence and odd failure detection.

## Decision

1. **Add a switch:** `camunda.cluster.network.udp-enabled`, default `true`, so existing clusters do
   not change. It applies to brokers and standalone gateways.

2. **`NettyMessagingService` also implements `UnicastService`.** It already owns the TCP transport,
   so TCP unicast reuses its connection pool, TLS, compression, and heartbeats. No new Netty
   resources are created.

3. **`CompositeUnicastService` picks the transport.** `NettyNetworkService` builds it from the
   switch. It sends over one transport and listens on all of them:

   | `udp-enabled`    | Sends with | Listens on |
   |------------------|------------|------------|
   | `true` (default) | UDP        | UDP + TCP  |
   | `false`          | TCP        | TCP        |

   With UDP disabled, `NettyUnicastService` is never created, so no UDP socket is bound. Listening
   on both in UDP mode allows rolling changes: a node with UDP disabled can still reach the others.
   The reverse is not true: UDP gossip to a node with UDP disabled is lost. That node still gets
   updates from SWIM sync (TCP), up to one sync interval later. Probes use TCP, so failure
   detection is unaffected.

4. **TCP unicast uses the same subject as UDP.** Registering a handler replaces any other handler
   on the same subject. `DefaultClusterCommunicationService.consume()` registers both a messaging
   handler and a unicast listener on one subject, but the two do the same thing, so the
   replacement changes nothing. `removeListener` only removes the handler it registered.

5. **TCP unicast stays fire-and-forget.** `unicast(...)` returns nothing. Gossip runs every 250 ms,
   so send errors are logged at `debug` level, at most once every 10 s, without a stack trace.

6. **TCP unicast follows the `UnicastService` contract:** one listener per subject. A new listener
   replaces the old one, and only the current listener can remove itself.

7. **The router has no lifecycle.** `NettyNetworkService` starts and stops both transports, and
   logs the selected transport once at startup.

## Alternatives considered

- **Add a TCP mode inside `NettyUnicastService`.** Rejected: one class would own two transports, and
  readers would have to work out which mode each line belongs to.
- **A separate subject prefix for TCP unicast.** Rejected: the prefix would become a permanent wire
  contract between versions, only to avoid a replacement that changes nothing (see decision 4).
- **Encrypt UDP with DTLS.** Rejected: it fixes encryption but not routing, and adds a second TLS
  setup for one kind of message.
- **A separate `TcpUnicastService` that wraps the messaging service.** Works, but needs one more
  class for the same result.
- **Build a new TCP transport inside the unicast service.** Rejected: it would duplicate connection
  pooling, TLS, compression, and heartbeats.
- **Make gossip reliable and remove unreliable unicast.** Rejected as the default: it changes
  behaviour and load for every existing cluster. It stays an option once the TCP path is proven.
- **Put the switch under `camunda.cluster.membership`.** Rejected: that section configures SWIM, not
  `MessagingConfig`, so it would need extra wiring for no benefit.

## Consequences

- **Upgrade every node before disabling UDP anywhere.** A node with UDP disabled sends gossip over
  TCP. An older node has no handler for it, so the gossip is dropped without an error.
- **Keep the "listen on both" behaviour.** Without it, a rolling change stops gossip in both
  directions. `MixedUdpModeIT` guards this.
- **Cost:** UDP gossip is one datagram. TCP gossip reuses a pooled connection, but for an
  unreachable peer, each gossip round tries to open a connection. Failure detection does not
  change, because its probes already use TCP.
- **Scope:** only Camunda's cluster protocols. The JVM can still use UDP, for example for DNS.
- **Test impact:** `ClusteringRule.disconnect`/`connect` used to close the UDP socket by casting to
  `NettyUnicastService`. They now stop and start the whole network service, UDP and TCP.

## Source

- Issue: [camunda/camunda#61663](https://github.com/camunda/camunda/issues/61663)
- Discussion: https://camunda.slack.com/archives/C08MRKHJ0CD/p1785845950877019
