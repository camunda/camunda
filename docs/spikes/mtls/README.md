# Spike: mutual TLS for clients, gateways and cluster nodes

Epic: [camunda/product-hub#3915](https://github.com/camunda/product-hub/issues/3915).

Throwaway spike code. It shows what each connection needs and where the hard parts are; it is
not meant to be merged as is. Three spikes, each with its own section, then a combined summary:

1. [gRPC client to gRPC gateway](#1-grpc-client-to-grpc-gateway)
2. REST client to REST gateway (todo)
3. Cluster messaging, `NettyMessagingService` (todo)

UDP is out of scope here; it is covered by the separate no-UDP work.

Client-side mTLS (Java client and Spring starter, `CAMUNDA_MTLS_*` / `camunda.client.mtls.*`)
is already on `main` (`clients/java/.../impl/tls/TlsContexts.java`), so all three spikes are
server-side.

## Shared building blocks

`zeebe/util/.../util/tls/` (no new dependencies):

- `ClientCertificateMode`: `NONE` (default, today's behavior), `OPTIONAL`, `REQUIRED`.
- `PeerCertificateTrustManager`: an `X509ExtendedTrustManager` that
  1. checks the leaf's validity window (to report `EXPIRED` / `NOT_YET_VALID` distinctly),
  2. delegates chain validation to the JDK PKIX trust manager built from a PEM bundle that may
     hold several CAs (rotation),
  3. matches the leaf's SANs (DNS, IP, URI, email; exact, case-insensitive) against an allow-list,
     with the subject CN as opt-in fallback; an empty allow-list accepts any trusted certificate,
  4. reports every outcome to a `PeerVerificationListener`.

  It implements both `checkClientTrusted` and `checkServerTrusted`, so the cluster messaging
  spike can reuse it in both directions; hostname verification stays with the JDK delegate.

- Unified config shape, `io.camunda.configuration.security.ClientAuthentication`, reused per
  server with its own values:

  ```yaml
  camunda.api.grpc.ssl.client-auth:
    mode: required                 # none | optional | required
    trusted-certificates: /path/to/client-cas.pem
    allowed-identities: [worker.example.com, 10.0.0.12]
    common-name-fallback: false
  ```

## 1. gRPC client to gRPC gateway

### What changed

- `SecurityCfg.clientAuth` (`ClientAuthCfg`) on the gateway config, mapped from
  `camunda.api.grpc.ssl.client-auth.*` for both the standalone and the embedded gateway.
- `Gateway#configureClientAuth`: with a mode other than `NONE`, sets the trust manager and Netty
  `ClientAuth.REQUIRE` / `ClientAuth.OPTIONAL` on the existing server `SslContextBuilder`.
- `MeteredPeerVerificationListener`: WARN log per rejection with the presented subject and SANs,
  DEBUG log per acceptance, and two counters:
  - `zeebe.gateway.grpc.client.certificate.accepted{identity=<matched allow-list entry or *>}`
  - `zeebe.gateway.grpc.client.certificate.rejected{reason, enforced}`

  Tags only hold values bounded by configuration; the presented identity is logged, not tagged.

- `ClientCertificateAuthenticationTest` runs a real gateway and a real gRPC client against a
  throwaway PKI (`TestPki`, BouncyCastle, test scope only).

### Validated (test matrix)

|                     Scenario                      |               Result               |
|---------------------------------------------------|------------------------------------|
| `required`, allowed SAN                           | accepted, counted by identity      |
| `required`, no certificate                        | rejected at handshake              |
| `required`, certificate from an untrusted CA      | rejected at handshake (see 1)      |
| `required`, expired certificate                   | rejected, `EXPIRED`                |
| `required`, SAN not allowed                       | rejected, `IDENTITY_NOT_ALLOWED`   |
| `required`, only CN matches, fallback off / on    | rejected / accepted                |
| `required`, bundle with two CAs, a client of each | both accepted (CA rotation)        |
| `required`, empty allow-list                      | any trusted cert accepted          |
| `optional`, no certificate                        | accepted                           |
| `optional`, untrusted and disallowed certificate  | accepted, counted `enforced=false` |
| default (`none`)                                  | unchanged                          |

Rejections surface on the client as `UNAVAILABLE` with the TLS alert as cause; the gateway never
sees a gRPC call.

### Findings

1. **The CA list in the certificate request hides bad clients.** The server sends the names of
   the trusted CAs; a client's key manager (including the Java client's) only presents a
   certificate issued by one of them. A client holding a certificate from another CA therefore
   presents *nothing*, the trust manager is never called, and the rejection is neither logged
   with its SAN nor counted. That breaks `optional` as an observation mode, so in `optional` the
   spike sends an empty CA list (every client presents what it has). `required` keeps sending it,
   which lets clients holding several certificates pick the right one. Decision needed: keep this
   split, or always send an empty list to get better rejection logs?
2. **"No certificate" under `required` is invisible to the trust manager.** The TLS engine fails
   the handshake before any callback. To log and count it we need a hook on handshake failure:
   grpc-java's `ServerTransportFilter` only sees completed handshakes, so this means a Netty
   handler, i.e. a custom `ProtocolNegotiator` (grpc-java internal/experimental API). Alternative:
   run Netty in `OPTIONAL` even for `required` and reject cert-less transports right after the
   handshake from a transport filter. That counts every case but rejects after the handshake
   instead of during it.
3. **No peer address in logs.** On the server side the `SSLEngine` gRPC creates has no peer
   host/port, so log lines say `peer=unknown`. Fixing this needs the same Netty-level hook as (2).
4. **`optional` semantics.** The epic's rollout ("set to optional, review the log, then required")
   only works if `optional` lets *failing* certificates through. Netty/JDK `want` mode does not:
   a presented but invalid certificate still fails the handshake. The spike makes the trust
   manager non-enforcing in `optional`, so any certificate is accepted and failures are only
   reported. Security-wise `optional` is then no stronger than `none`; this needs to be explicit
   in the docs. The same mode can serve as the "permissive" mode for node-to-node rollout.
5. **Identity is not propagated to the request.** Not needed now (certificates grant no
   authorizations), but the peer certificate is reachable from a gRPC interceptor via
   `Grpc.TRANSPORT_ATTR_SSL_SESSION` when certificate-to-principal mapping comes up.
6. **Logging volume.** One log line per handshake, not per request; gRPC connections are
   long-lived, so this is fine for gRPC. Rejections are WARN with no rate limit; a misconfigured
   fleet reconnecting in a loop would flood the log. Consider rate limiting.
7. Wildcard entries in the allow-list (`*.workers.example.com`) are not supported; exact match
   only. RFC 9525 discourages wildcards in presented identities, but an allow-list pattern is a
   different question for product.
8. `netty-tcnative-boringssl-static` is on the gateway classpath and `GrpcSslContexts` prefers it,
   so the handshake runs in BoringSSL; Netty wraps the custom `X509ExtendedTrustManager`, so the
   Java-side checks still apply. Relevant for the open FIPS question.

