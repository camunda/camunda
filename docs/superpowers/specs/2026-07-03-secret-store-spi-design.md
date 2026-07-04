# Secret Store SPI + File-Based Implementation

**Issue:** [#56560](https://github.com/camunda/camunda/issues/56560)  
**Date:** 2026-07-03

## Goal

Define the `SecretStore` SPI that all secret store implementations will implement, plus a file-based
implementation for local development and C8Run. Wiring into broker and gateway is out of scope for
this PR; that follows in a subsequent issue.

## Module Structure

New standalone top-level module `secret-store/` with two sub-modules, registered in the root
`pom.xml`:

```
secret-store/
  pom.xml                          (parent, packaging=pom)
  secret-store-api/
    pom.xml                        (artifactId: camunda-secret-store-api)
    src/main/java/io/camunda/secretstore/
      SecretStore.java
      SecretRef.java
      SecretResolutionResult.java
      SecretErrorCode.java
  secret-store-file/
    pom.xml                        (artifactId: camunda-secret-store-file, depends on api)
    src/main/java/io/camunda/secretstore/file/
      FileBasedSecretStore.java
```

- `secret-store-api` has no Spring dependency — pure Java.
- `secret-store-file` has no Spring dependency — `FileBasedSecretStore` takes a `Path` via its
  constructor. Spring wiring (turning it into a `@Bean`) is deferred to the broker/gateway wiring
  follow-up.
- Future stores (GCP #56579, AWS #56575) each add their own sub-module (e.g. `secret-store-gcp`,
  `secret-store-aws`) and depend on `secret-store-api`.
- Tenancy is handled outside the SPI: each logical tenant is configured with its own `SecretStore`
  instance. `SecretRef` has no tenant dimension; stores are unaware of tenancy.

## Types (`secret-store-api`)

### `SecretRef`

A thin record used as a map key throughout the API. The compact constructor rejects null or blank
names:

```java
public record SecretRef(String name) {
  public SecretRef {
    Objects.requireNonNull(name, "name must not be null");
    if (name.isBlank()) throw new IllegalArgumentException("name must not be blank");
  }
}
```

### `SecretErrorCode`

Typed error categories. Callers switch on the code; store implementations map their SDK-specific
exceptions to these codes, keeping SDK types behind the SPI boundary:

```java
public enum SecretErrorCode {
  NOT_FOUND,        // the referenced secret does not exist in the store
  ACCESS_DENIED,    // caller lacks permission to read the secret
  STORE_UNAVAILABLE,// the backing store cannot be reached or is misconfigured
  INVALID_REF       // the ref is syntactically invalid for this store
}
```

### `SecretResolutionResult`

A sealed interface with two subtypes, exhaustively pattern-matchable. `Resolved.toString()` masks
the secret value to prevent accidental logging:

```java
public sealed interface SecretResolutionResult
    permits SecretResolutionResult.Resolved, SecretResolutionResult.Failed {

  record Resolved(String value) implements SecretResolutionResult {
    @Override public String toString() {
      return "Resolved[value=***]";
    }
  }

  record Failed(SecretErrorCode code, String message, @Nullable Throwable cause)
      implements SecretResolutionResult {}
}
```

### `SecretStore`

The SPI interface. Extends `AutoCloseable` so store implementations that hold resources (e.g. gRPC
channels, HTTP connection pools) can participate in orderly shutdown. The default `close()` is a
no-op for stores that hold no resources:

```java
public interface SecretStore extends AutoCloseable {

  /**
   * Resolves a set of secret references in a single call.
   *
   * <p>Returns a result for <em>every</em> ref in the input set. Never throws — store-level
   * failures (unreachable backend, missing file, etc.) are reported as
   * {@link SecretResolutionResult.Failed} with code {@link SecretErrorCode#STORE_UNAVAILABLE}.
   */
  Map<SecretRef, SecretResolutionResult> resolve(Set<SecretRef> refs);

  /**
   * Lists all secret references known to this store.
   */
  Collection<SecretRef> list();

  @Override
  default void close() {}
}
```

**Contract guarantees (documented on the interface):**
- `resolve()` never throws — any failure is returned as `Failed`.
- The returned map contains an entry for *every* ref in the input set.
- `list()` may throw `SecretStoreUnavailableException` on store-level failures (different contract
from `resolve()` — there is no per-item result type to carry errors).
- Implementations must be thread-safe.

Example call-site pattern:

```java
for (var entry : secretStore.resolve(refs).entrySet()) {
  switch (entry.getValue()) {
    case SecretResolutionResult.Resolved r -> useSecret(entry.getKey(), r.value());
    case SecretResolutionResult.Failed  f -> raiseIncident(entry.getKey(), f.code(), f.message());
  }
}
```

## `FileBasedSecretStore` (`secret-store-file`)

Backed by a mounted `.properties` file (`key=value` per line), read with UTF-8 encoding. Lazy-
loading: the file is read on every `resolve()` and `list()` call, so secret rotation is picked up
without a restart. Rotation must be performed via an atomic file rename (Kubernetes secret mounts
do this automatically).

- No mutable state — holds only a `final Path filePath`.
- If the file is missing or unreadable, every requested ref resolves to
  `Failed(STORE_UNAVAILABLE, ...)` — `resolve()` does not throw.
- No external dependencies beyond the JDK.

```java
public final class FileBasedSecretStore implements SecretStore {

  private static final Logger LOG = LoggerFactory.getLogger(FileBasedSecretStore.class);

  private final Path filePath;

  public FileBasedSecretStore(final Path filePath) {
    this.filePath = filePath;
  }

  @Override
  public Map<SecretRef, SecretResolutionResult> resolve(final Set<SecretRef> refs) {
    final Properties props;
    try {
      props = loadProperties();
    } catch (final SecretStoreUnavailableException e) {
      LOG.warn("Secret store unavailable at '{}': {}", filePath, e.getMessage());
      return refs.stream().collect(toMap(
          ref -> ref,
          ref -> new SecretResolutionResult.Failed(
              STORE_UNAVAILABLE,
              Objects.requireNonNullElse(e.getMessage(), "Secret store unavailable: " + filePath),
              e.getCause())));
    }
    // Never log resolved values — only ref names and counts are safe to log
    LOG.debug("Resolving {} secret refs from '{}'", refs.size(), filePath);
    return refs.stream().collect(toMap(
        ref -> ref,
        ref -> {
          final var value = props.getProperty(ref.name());
          return value != null
              ? new SecretResolutionResult.Resolved(value)
              : new SecretResolutionResult.Failed(
                    NOT_FOUND, "Secret not found: " + ref.name(), null);
        }));
  }

  @Override
  public Collection<SecretRef> list() {
    final var props = loadProperties();
    LOG.debug("Listing {} secrets from '{}'", props.size(), filePath);
    return props.stringPropertyNames().stream()
        .map(SecretRef::new)
        .toList();
  }

  private Properties loadProperties() {
    final var props = new Properties();
    try (final var reader = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
      props.load(reader);
    } catch (final IOException e) {
      throw new SecretStoreUnavailableException(
          "Failed to load secrets file '" + filePath + "': " + e.getMessage(), e);
    }
    return props;
  }
}
```

## Testing

### `secret-store-api` — types only, no heavy tests needed

- `SecretRefTest` — record equality/hashCode, null name rejected, blank name rejected, usable as map key.
- `SecretResolutionResultTest` — `Resolved` carries value, `Resolved.toString()` masks the value,
  `Failed` carries code + message + cause, pattern switch compiles exhaustively.

### `secret-store-file` — `FileBasedSecretStoreTest`

Uses JUnit 5 `@TempDir`.

|                      Test                      |                                                   Asserts                                                    |
|------------------------------------------------|--------------------------------------------------------------------------------------------------------------|
| `shouldResolveKnownSecret`                     | Single known ref → `Resolved` with correct value                                                             |
| `shouldReturnFailedForUnknownRef`              | Ref not in file → `Failed(NOT_FOUND)`                                                                        |
| `shouldResolveMultipleRefsInOneBatch`          | Mix of known/unknown in one call; all refs present in result map                                             |
| `shouldListAllSecrets`                         | `list()` returns all keys from file                                                                          |
| `shouldPickUpRotatedValue`                     | Overwrite file between two `resolve()` calls; second returns new value                                       |
| `shouldReturnStoreUnavailableWhenFileMissing`  | Path does not exist → `Failed(STORE_UNAVAILABLE)` for every ref                                              |
| `shouldHandleUtf8Values`                       | Secret value containing non-Latin-1 characters round-trips correctly                                         |
| `shouldHandleValuesWithSpecialPropertiesChars` | Value containing `=`, `:`, `\`, leading whitespace                                                           |
| `shouldBeThreadSafe`                           | 10 threads × 100 calls via `ExecutorService` + `CountDownLatch`; no exceptions, results consistent with file |

**Note:** When the second store implementation (GCP/AWS) is added, extract an abstract
`SecretStoreContractTest` from `FileBasedSecretStoreTest` into a shared test-jar in
`secret-store-api`. All store implementations then extend it to pin the SPI contract.

## Out of Scope

- Retry/backoff wrapper — deferred; can be added later as a `RetryingSecretStore` decorator.
- Spring `@Bean` wiring into broker and gateway — follow-up issue.
- Async `resolve()` signature — re-evaluate when implementing GCP/AWS stores; changing the
  signature after multiple implementations exist would be a breaking change, so this should be
  a conscious decision at that point.
- JSON file format — `.properties` is sufficient for Phase 1.
- GCP / AWS store implementations — tracked in #56579 and #56575 respectively.

