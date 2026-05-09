# ADR-0002: JRE 25 Docker Base Images While Retaining JRE 21 Runtime Support

## Status

Accepted

## Deciders

- Nicolas Pepin-Perreault ([@npepinpe](https://github.com/npepinpe))
- Lena Schoenburg ([@lenaschoenburg](https://github.com/lenaschoenburg))

## Context

Docker images previously used JRE 21. JRE 21 remains a supported runtime for self-managed
bare-metal deployments; upgrading the minimum runtime across the board would be a breaking change
requiring careful communication.

JRE 25 (latest LTS) brings two relevant improvements:

- **Virtual Thread improvements**: reduces carrier thread pinning, which caused throughput
  degradation under load.
- **General performance improvements**.

One concern raised was test coverage regression: E2E and reliability tests run against Docker
images, so upgrading to JRE 25 means they no longer exercise JRE 21 Virtual Thread behavior.
However, coverage of pinning was already incomplete on JRE 21 — production systems may use custom
exporters or agents that introduce pinning, and deployment topologies affect the common fork join
pool in ways CI cannot reproduce. Pinning leads to deadlocks at worst, not data loss. Since most
users run Docker images, resolving Virtual Thread issues for that majority was judged to outweigh
the risk of missing pinning regressions for the small subset of JRE 21 bare-metal users.

## Decision

Upgrade Docker base images to JRE 25 (`openjre-base:25-dev` hardened and
`eclipse-temurin:25.0.2_10-jre-noble` public fallback). This change is scoped to Docker images
only — bare-metal deployments continue to target JRE 21 and are unaffected. The Maven build target
remains 21. CI continues to validate against JRE 21. No dedicated JRE 21 Docker test job is
introduced at this time.

Because bare-metal support is unchanged and the build target remains 21, this change is relatively
transparent to users: containerized deployments benefit from the JRE 25 improvements, while
bare-metal users observe no difference.

We accept that a small minority of users may still encounter Virtual Thread pinning issues that CI
could have — but would not necessarily have — detected had we kept JRE 21 Docker images.

## Consequences

### Positive

- Virtual Thread pinning issues resolved for the majority of users.
- General JVM performance improvements for all containerized deployments.

### Negative

- E2E and reliability tests no longer exercise JRE 21 JVM behavior; Virtual Thread pinning
  regressions on JRE 21 will not be caught by CI unless a dedicated job is added.
- Bare-metal JRE 21 users receive no direct benefit from this change.
