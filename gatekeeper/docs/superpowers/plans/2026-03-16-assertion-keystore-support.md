# Assertion & Keystore Configuration Support

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add full `private_key_jwt` assertion/keystore configuration to gatekeeper so that `AssertionJwkProvider` works, replacing the current `UnsupportedOperationException` stub and enabling removal of the security-core assertion config classes.

**Architecture:** Add an `AssertionConfig` record to `gatekeeper-domain` holding keystore path, password, key alias, key password, and kid generation settings. Extend `OidcConfig` with an optional `AssertionConfig` field. Add corresponding properties binding in `GatekeeperProperties.OidcProperties`. Port the original `AssertionJwkProvider` logic from the monorepo's `authentication` module into gatekeeper's existing stub class.

**Tech Stack:** Java 21, Nimbus JOSE JWT, `java.security.KeyStore` (PKCS12)

---

## File Map

| File | Action | Purpose |
|---|---|---|
| `gatekeeper-domain/src/main/java/io/camunda/gatekeeper/config/AssertionConfig.java` | Create | Immutable record holding assertion/keystore/kid config |
| `gatekeeper-domain/src/main/java/io/camunda/gatekeeper/config/OidcConfig.java` | Modify | Add `AssertionConfig assertion` field |
| `gatekeeper-domain/src/test/java/io/camunda/gatekeeper/unit/config/OidcConfigTest.java` | Modify | Update `createConfig` helpers for new constructor parameter |
| `gatekeeper-domain/src/test/java/io/camunda/gatekeeper/unit/config/AssertionConfigTest.java` | Create | Tests for validation logic |
| `gatekeeper-spring-boot-starter/src/main/java/io/camunda/gatekeeper/spring/config/GatekeeperProperties.java` | Modify | Add `AssertionProperties` inner class to `OidcProperties` |
| `gatekeeper-spring-boot-starter/src/main/java/io/camunda/gatekeeper/spring/oidc/AssertionJwkProvider.java` | Modify | Replace stub with working keystore-based JWK creation |
| `gatekeeper-spring-boot-starter/src/test/java/io/camunda/gatekeeper/spring/unit/oidc/AssertionJwkProviderTest.java` | Create | Tests for JWK creation from keystore |

---

## Chunk 1: Domain model — AssertionConfig record

### Task 1: Create AssertionConfig record

**Files:**
- Create: `gatekeeper-domain/src/main/java/io/camunda/gatekeeper/config/AssertionConfig.java`
- Test: `gatekeeper-domain/src/test/java/io/camunda/gatekeeper/unit/config/AssertionConfigTest.java`

- [ ] **Step 1: Write the AssertionConfig record**

```java
package io.camunda.gatekeeper.config;

/**
 * Immutable configuration for private_key_jwt client assertion. Holds keystore location,
 * credentials, and kid (Key ID) generation settings.
 */
public record AssertionConfig(
    String keystorePath,
    String keystorePassword,
    String keyAlias,
    String keyPassword,
    KidSource kidSource,
    KidDigestAlgorithm kidDigestAlgorithm,
    KidEncoding kidEncoding,
    KidCase kidCase) {

  /** Defines whether the kid is derived from the certificate or its public key. */
  public enum KidSource {
    CERTIFICATE,
    PUBLIC_KEY
  }

  /** Digest algorithm used to generate the kid. */
  public enum KidDigestAlgorithm {
    SHA1,
    SHA256
  }

  /** Encoding format for the kid digest bytes. */
  public enum KidEncoding {
    HEX,
    BASE64URL
  }

  /** Case transformation for hex-encoded kid strings. Only applicable when encoding is HEX. */
  public enum KidCase {
    UPPER,
    LOWER
  }

  public AssertionConfig {
    if (kidSource == null) {
      kidSource = KidSource.PUBLIC_KEY;
    }
    if (kidDigestAlgorithm == null) {
      kidDigestAlgorithm = KidDigestAlgorithm.SHA256;
    }
    if (kidEncoding == null) {
      kidEncoding = KidEncoding.BASE64URL;
    }
  }

  /** Validates that kidCase is only set when kidEncoding is HEX. */
  public void validate() {
    if (kidCase != null && kidEncoding != KidEncoding.HEX) {
      throw new IllegalStateException("kidCase can only be set when kidEncoding is HEX");
    }
  }

  /** Returns true if a keystore path is configured, indicating private_key_jwt is intended. */
  public boolean isConfigured() {
    return keystorePath != null && !keystorePath.isBlank();
  }
}
```

- [ ] **Step 2: Write AssertionConfigTest**

```java
package io.camunda.gatekeeper.unit.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.gatekeeper.config.AssertionConfig;
import io.camunda.gatekeeper.config.AssertionConfig.KidCase;
import io.camunda.gatekeeper.config.AssertionConfig.KidDigestAlgorithm;
import io.camunda.gatekeeper.config.AssertionConfig.KidEncoding;
import io.camunda.gatekeeper.config.AssertionConfig.KidSource;
import org.junit.jupiter.api.Test;

final class AssertionConfigTest {

  @Test
  void defaultsAreAppliedForNullEnums() {
    final var config = new AssertionConfig("/path", "pass", "alias", "keypass", null, null, null, null);
    assertThat(config.kidSource()).isEqualTo(KidSource.PUBLIC_KEY);
    assertThat(config.kidDigestAlgorithm()).isEqualTo(KidDigestAlgorithm.SHA256);
    assertThat(config.kidEncoding()).isEqualTo(KidEncoding.BASE64URL);
  }

  @Test
  void validateThrowsWhenKidCaseSetWithNonHexEncoding() {
    final var config = new AssertionConfig("/path", "pass", "alias", "keypass",
        KidSource.PUBLIC_KEY, KidDigestAlgorithm.SHA256, KidEncoding.BASE64URL, KidCase.UPPER);
    assertThatThrownBy(config::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("kidCase can only be set when kidEncoding is HEX");
  }

  @Test
  void validatePassesWhenKidCaseSetWithHexEncoding() {
    final var config = new AssertionConfig("/path", "pass", "alias", "keypass",
        KidSource.PUBLIC_KEY, KidDigestAlgorithm.SHA256, KidEncoding.HEX, KidCase.UPPER);
    config.validate(); // should not throw
  }

  @Test
  void isConfiguredReturnsFalseForNullPath() {
    final var config = new AssertionConfig(null, null, null, null, null, null, null, null);
    assertThat(config.isConfigured()).isFalse();
  }

  @Test
  void isConfiguredReturnsTrueForNonBlankPath() {
    final var config = new AssertionConfig("/path/to/keystore.p12", "pass", "alias", "keypass",
        null, null, null, null);
    assertThat(config.isConfigured()).isTrue();
  }
}
```

- [ ] **Step 3: Run tests**

Run: `cd gatekeeper && ../mvnw test -pl gatekeeper-domain -Dtest="AssertionConfigTest" -Dsurefire.failIfNoSpecifiedTests=false`

Expected: All tests PASS

- [ ] **Step 4: Compile domain module**

Run: `cd gatekeeper && ../mvnw compile -pl gatekeeper-domain -q`

Expected: BUILD SUCCESS

---

### Task 2: Add assertion field to OidcConfig

**Files:**
- Modify: `gatekeeper-domain/src/main/java/io/camunda/gatekeeper/config/OidcConfig.java`
- Modify: `gatekeeper-domain/src/test/java/io/camunda/gatekeeper/unit/config/OidcConfigTest.java`

- [ ] **Step 1: Add AssertionConfig field to OidcConfig record**

Add `AssertionConfig assertion` as the last parameter of the record. Update the compact constructor to default it to `null` (no assertion configured).

The record signature becomes:
```java
public record OidcConfig(
    String issuerUri,
    String clientId,
    String clientSecret,
    String jwkSetUri,
    List<String> additionalJwkSetUris,
    String authorizationUri,
    String tokenUri,
    String endSessionEndpointUri,
    String usernameClaim,
    String clientIdClaim,
    String groupsClaim,
    boolean preferUsernameClaim,
    String scope,
    List<String> audiences,
    String redirectUri,
    Duration clockSkew,
    boolean idpLogoutEnabled,
    String grantType,
    String clientAuthenticationMethod,
    String registrationId,
    AssertionConfig assertion) {
```

The existing compact constructor normalizes lists. No change needed for the new field — `null` means no assertion.

- [ ] **Step 2: Update OidcConfigTest createConfig helpers**

Every call to `new OidcConfig(...)` in this test needs the new `assertion` parameter appended (pass `null`).

The `nullListsAreNormalizedToEmpty` test constructs OidcConfig with all nulls — add one more `null` at the end.

The `createConfig` helper gets `null` as the last argument.

- [ ] **Step 3: Fix compilation across both modules**

The `OidcConfig` constructor is called from:
- `GatekeeperProperties.OidcProperties.toOidcConfig()` — add `null` as the last argument (assertion properties will be added in Task 3)
- `OidcConfigurationAdapter.toOidcConfig()` in the authentication module — add `null` as the last argument
- Any other callers found by compilation

Run: `cd gatekeeper && ../mvnw compile -T1C -q`

If compilation fails, fix remaining callers by appending `null` for the assertion parameter.

- [ ] **Step 4: Run all domain tests**

Run: `cd gatekeeper && ../mvnw test -pl gatekeeper-domain -q`

Expected: All tests PASS

---

## Chunk 2: Properties binding & AssertionJwkProvider implementation

### Task 3: Add AssertionProperties to GatekeeperProperties

**Files:**
- Modify: `gatekeeper-spring-boot-starter/src/main/java/io/camunda/gatekeeper/spring/config/GatekeeperProperties.java`

- [ ] **Step 1: Add AssertionProperties inner class**

Inside `GatekeeperProperties`, add a new inner class after `ProvidersProperties`:

```java
/** Assertion properties for private_key_jwt matching {@code ...oidc.assertion.*}. */
public static class AssertionProperties {
  private String keystorePath;
  private String keystorePassword;
  private String keyAlias;
  private String keyPassword;
  private String kidSource = "PUBLIC_KEY";
  private String kidDigestAlgorithm = "SHA256";
  private String kidEncoding = "BASE64URL";
  private String kidCase;

  public AssertionConfig toAssertionConfig() {
    return new AssertionConfig(
        keystorePath,
        keystorePassword,
        keyAlias,
        keyPassword,
        kidSource != null ? AssertionConfig.KidSource.valueOf(kidSource) : null,
        kidDigestAlgorithm != null
            ? AssertionConfig.KidDigestAlgorithm.valueOf(kidDigestAlgorithm)
            : null,
        kidEncoding != null ? AssertionConfig.KidEncoding.valueOf(kidEncoding) : null,
        kidCase != null ? AssertionConfig.KidCase.valueOf(kidCase) : null);
  }

  // getters and setters for all 8 fields
}
```

- [ ] **Step 2: Add assertion field to OidcProperties**

Add `private AssertionProperties assertion = new AssertionProperties();` to `OidcProperties`, with getter/setter.

- [ ] **Step 3: Update OidcProperties.toOidcConfig to pass assertion config**

In both `toOidcConfig()` and `toOidcConfig(String overrideRegistrationId)`, replace the trailing `overrideRegistrationId)` with `overrideRegistrationId, assertion.toAssertionConfig())`.

Note: only pass a non-null `AssertionConfig` if the keystore path is configured. If not configured, pass `null` to keep the `OidcConfig.assertion` null (meaning no private_key_jwt).

```java
final var assertionConfig = assertion.toAssertionConfig();
// ... in the OidcConfig constructor call, add:
assertionConfig.isConfigured() ? assertionConfig : null
```

- [ ] **Step 4: Compile**

Run: `cd gatekeeper && ../mvnw compile -T1C -q`

Expected: BUILD SUCCESS

---

### Task 4: Implement AssertionJwkProvider

**Files:**
- Modify: `gatekeeper-spring-boot-starter/src/main/java/io/camunda/gatekeeper/spring/oidc/AssertionJwkProvider.java`
- Create: `gatekeeper-spring-boot-starter/src/test/java/io/camunda/gatekeeper/spring/unit/oidc/AssertionJwkProviderTest.java`

- [ ] **Step 1: Replace the stub with working implementation**

Port the logic from the original `authentication/src/main/java/io/camunda/authentication/config/AssertionJwkProvider.java` (visible via `git show 4150e35be8d:authentication/src/main/java/io/camunda/authentication/config/AssertionJwkProvider.java`).

The key changes from the original:
- Read `AssertionConfig` from `OidcConfig.assertion()` instead of `OidcAuthenticationConfiguration.getAssertion()`
- Use `AssertionConfig.keystorePath()` instead of `config.getKeystore().getPath()`
- Use `AssertionConfig.KidSource` etc. instead of `AssertionConfiguration.KidSource`
- Load the keystore using `java.security.KeyStore.getInstance("PKCS12")` and `FileInputStream`
- The `createJwk(String clientRegistrationId)` method should:
  1. Look up `OidcConfig` from the repository
  2. Get `OidcConfig.assertion()`
  3. If assertion is null or not configured, throw `IllegalStateException("No assertion configuration found for registration ID: " + clientRegistrationId)`
  4. Validate the assertion config
  5. Load keystore, extract private key and certificate
  6. Build RSA JWK with kid, x5c chain, and x5t#S256 thumbprint

```java
public JWK createJwk(final String clientRegistrationId) {
  final var oidcConfig = oidcConfigRepository.getOidcConfigById(clientRegistrationId);
  if (oidcConfig == null) {
    throw new IllegalArgumentException(
        "No OIDC configuration found for registration ID: " + clientRegistrationId);
  }
  final var assertionConfig = oidcConfig.assertion();
  if (assertionConfig == null || !assertionConfig.isConfigured()) {
    throw new IllegalStateException(
        "No assertion/keystore configuration found for registration ID: "
            + clientRegistrationId
            + ". Configure camunda.security.authentication.oidc.assertion.keystore-path "
            + "to enable private_key_jwt client authentication.");
  }
  assertionConfig.validate();
  try {
    final var keyStore = loadKeystore(assertionConfig);
    final var alias = assertionConfig.keyAlias();
    final var password = assertionConfig.keyPassword().toCharArray();
    final var pk = (PrivateKey) keyStore.getKey(alias, password);
    final var cert = keyStore.getCertificate(alias);
    final var pub = (RSAPublicKey) cert.getPublicKey();
    return new RSAKey.Builder(pub)
        .privateKey(pk)
        .x509CertChain(List.of(Base64.encode(cert.getEncoded())))
        .keyID(generateKid(cert, assertionConfig))
        .x509CertSHA256Thumbprint(thumbprintSha256(cert))
        .build();
  } catch (final Exception e) {
    throw new IllegalStateException("Unable to load keystore for client: " + clientRegistrationId, e);
  }
}

private static KeyStore loadKeystore(final AssertionConfig config) throws Exception {
  final var keyStore = KeyStore.getInstance("PKCS12");
  try (final var fis = new FileInputStream(config.keystorePath())) {
    keyStore.load(fis, config.keystorePassword().toCharArray());
  }
  return keyStore;
}
```

Port the `generateKid`, `thumbprintSha256`, `getKidDigestAlgorithmInstance`, `getKidSourceBytes`, and `getHexFormatWithCase` private methods from the original, adapting enum references to use `AssertionConfig.*` enums.

- [ ] **Step 2: Write AssertionJwkProviderTest**

Create a test keystore (PKCS12) in `src/test/resources/keystore/test-keystore.p12` using a keytool command, or generate one programmatically in the test setup. Then test:

1. `createJwk` returns a valid RSA JWK when keystore is configured correctly
2. `createJwk` throws `IllegalArgumentException` when registration ID is not found
3. `createJwk` throws `IllegalStateException` when assertion config is null
4. The generated kid matches expected format based on kid config settings

For generating a test keystore programmatically in the test:
```java
@BeforeAll
static void createTestKeystore() throws Exception {
  final var keyStore = KeyStore.getInstance("PKCS12");
  keyStore.load(null, null);
  final var keyPairGenerator = java.security.KeyPairGenerator.getInstance("RSA");
  keyPairGenerator.initialize(2048);
  final var keyPair = keyPairGenerator.generateKeyPair();
  // Create a self-signed certificate... (use sun.security or Bouncy Castle)
}
```

Alternatively, use a pre-generated test keystore checked into `src/test/resources/`.

- [ ] **Step 3: Run tests**

Run: `cd gatekeeper && ../mvnw test -pl gatekeeper-spring-boot-starter -Dtest="AssertionJwkProviderTest" -Dsurefire.failIfNoSpecifiedTests=false`

Expected: All tests PASS

- [ ] **Step 4: Run full test suite**

Run: `cd gatekeeper && ../mvnw test`

Expected: All tests PASS (including existing 110 tests + new tests)

- [ ] **Step 5: Run spotless**

Run: `cd gatekeeper && ../mvnw spotless:apply`

- [ ] **Step 6: Commit**

```bash
git add -A gatekeeper/
git commit -m "feat: add assertion/keystore configuration for private_key_jwt support"
```

---

## Chunk 3: Verification

### Task 5: Verify property binding end-to-end

- [ ] **Step 1: Verify property binding path**

Confirm the full property path works:
```yaml
camunda:
  security:
    authentication:
      oidc:
        client-authentication-method: private_key_jwt
        assertion:
          keystore-path: /path/to/keystore.p12
          keystore-password: changeit
          key-alias: my-key
          key-password: changeit
          kid-source: PUBLIC_KEY        # or CERTIFICATE
          kid-digest-algorithm: SHA256  # or SHA1
          kid-encoding: BASE64URL       # or HEX
          kid-case: UPPER               # only with HEX encoding
```

This can be verified through an `ApplicationContextRunner` test that sets these properties and checks that the `OidcConfig.assertion()` is populated.

- [ ] **Step 2: Verify ArchUnit domain rules still pass**

Run: `cd gatekeeper && ../mvnw test -pl gatekeeper-domain -Dtest="DomainArchTest"`

Expected: PASS — `AssertionConfig` is a record in the `config` package with no framework dependencies.

- [ ] **Step 3: Verify multi-provider assertion works**

Named providers should also support assertion config:
```yaml
camunda:
  security:
    authentication:
      providers:
        oidc:
          entra-id:
            client-authentication-method: private_key_jwt
            assertion:
              keystore-path: /path/to/entra-keystore.p12
```

This works automatically because `ProvidersProperties` maps to `OidcProperties` which now has `AssertionProperties`.
