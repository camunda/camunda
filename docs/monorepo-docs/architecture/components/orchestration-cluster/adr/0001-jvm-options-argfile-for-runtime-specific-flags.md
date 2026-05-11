# ADR-0001: Optional jvm.options @argfile for Runtime-Specific JVM Flags

## Status

Accepted

## Deciders

- Nicolas Pepin-Perreault ([@npepinpe](https://github.com/npepinpe))
- Lena Schoenburg ([@lenaschoenburg](https://github.com/lenaschoenburg))
- Carlo Sana ([@entangled90](https://github.com/entangled90))

## Context

Moving to JRE 25 base images surfaces JVM warnings requiring flags unavailable on older JREs:

- `--enable-native-access=ALL-UNNAMED` (jffi): safe on JRE 17+, added unconditionally to `jvm.module.opens`.
- `--sun-misc-unsafe-memory-access=allow` (Kryo): JRE 23+ only — passing it on JRE 21 causes a hard startup failure.

For the JRE 23+ flag, alternatives considered:

1. **`ENV JAVA_TOOL_OPTIONS` in Dockerfiles** — doesn't cover the distribution tarball; overridable.
2. **Version check in launcher** — calls `java -version` on every startup; fragile.
3. **Bake into `extraJvmArguments`** — breaks JRE 21 tarball users.
4. **Optional `@argfile`** — launcher loads `config/jvm.options` if present; absent from tarball, shipped only in Docker images.

Option 4 requires custom appassembler script templates. The maintenance concern was weighed against
the fact that the plugin has not been updated since 2015 and is effectively frozen; the templates
are a one-time copy unlikely to diverge.

## Decision

Load an optional `config/jvm.options` as a JVM `@argfile` in all launcher scripts (option 4).
Custom appassembler templates in `dist/src/main/scripts/` — verbatim copies of the plugin's 2.1.0
defaults — add this check. Docker images `COPY` `zeebe/docker/utils/jvm.options` into `config/`;
the distribution tarball ships no such file.

## Consequences

### Positive

- Runtime-specific flags stay at the deployment boundary, not the distribution.
- `config/jvm.options` is a general extension point for operators.
- No startup overhead.

### Negative

- Custom appassembler templates must be diffed on any future plugin upgrade, though such upgrades
  are unlikely given the plugin has been frozen since 2015.
- `optimize-startup.sh` uses its own startup script and requires the same pattern maintained
  separately.
