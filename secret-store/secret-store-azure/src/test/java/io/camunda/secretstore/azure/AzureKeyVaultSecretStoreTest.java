/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.azure;

import static io.camunda.secretstore.SecretErrorCode.NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import com.azure.core.http.HttpClient;
import com.azure.core.util.Configuration;
import io.camunda.secretstore.SecretResolutionResult;
import io.camunda.secretstore.SecretResolutionResult.Failed;
import io.camunda.secretstore.SecretResolutionResult.Resolved;
import io.camunda.secretstore.SecretStoreMetricsDoc;
import io.camunda.zeebe.test.util.logging.LogCapturer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.logging.log4j.Level;
import org.awaitility.Awaitility;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class AzureKeyVaultSecretStoreTest {

  private static final String PHYSICAL_TENANT = "pt1";
  private static final String STORE_ID = "default";
  private static final String VAULT_URL = "https://my-vault.vault.azure.net";

  @Test
  void shouldFanOutOneNamePerCallByDefault() {
    // when
    try (final var store = store(AzureKeyVaultStoreConfig.of(VAULT_URL), signedIn())) {

      // then
      assertThat(store.namesPerCall()).isEqualTo(1);
    }
  }

  @Test
  void shouldCoverEveryNameInOneCallWithAContainerSecret() {
    // when
    try (final var store = store(containerConfig(), signedIn())) {

      // then
      assertThat(store.namesPerCall()).isEqualTo(Integer.MAX_VALUE);
    }
  }

  @Test
  void shouldReportEveryNameAsNotFoundInContainerModeUntilItIsImplemented() {
    // given
    try (final var store = store(containerConfig(), signedIn())) {

      // when
      final var results = store.resolve(Set.of("a", "b"));

      // then
      assertThat(results).containsOnlyKeys("a", "b");
      assertThat(results.values())
          .allSatisfy(result -> assertThat(failure(result).code()).isEqualTo(NOT_FOUND));
      assertThat(store.list()).isEmpty();
    }
  }

  @Test
  void shouldReadTheLatestVersionOfThePrefixedSecret() {
    // given
    final var http = signedIn().putSecret("camunda-api-key", "the-key");
    try (final var store = store(prefixed("camunda-"), http)) {

      // when
      final var results = store.resolve(Set.of("api-key"));

      // then — no version in the path means Key Vault's latest
      assertThat(results).containsExactly(entry("api-key", new Resolved("the-key")));
      assertThat(secretPathsRead(http)).containsExactly("/secrets/camunda-api-key/");
    }
  }

  @Test
  void shouldReadTheBareNameWithoutAPrefix() {
    // given
    final var http = signedIn().putSecret("api-key", "the-key");
    try (final var store = store(AzureKeyVaultStoreConfig.of(VAULT_URL), http)) {

      // when
      final var results = store.resolve(Set.of("api-key"));

      // then
      assertThat(results).containsExactly(entry("api-key", new Resolved("the-key")));
      assertThat(secretPathsRead(http)).containsExactly("/secrets/api-key/");
    }
  }

  @Test
  void shouldReturnANewVersionWithoutARestart() {
    // given
    final var http = signedIn().putSecret("api-key", "first");
    try (final var store = store(AzureKeyVaultStoreConfig.of(VAULT_URL), http)) {
      assertThat(store.resolve(Set.of("api-key"))).containsValue(new Resolved("first"));

      // when — the store keeps no cache: the cache TTL is the wrapping CachingSecretStore's
      http.putSecret("api-key", "second");

      // then
      assertThat(store.resolve(Set.of("api-key"))).containsValue(new Resolved("second"));
    }
  }

  @Test
  void shouldPassTheNameAsWrittenAndLetTheVaultIgnoreItsCase() {
    // given
    final var http = signedIn().putSecret("db-password", "the-password");
    try (final var store = store(AzureKeyVaultStoreConfig.of(VAULT_URL), http)) {

      // when
      final var results = store.resolve(Set.of("Db-Password", "db-password"));

      // then
      assertThat(results)
          .containsOnly(
              entry("Db-Password", new Resolved("the-password")),
              entry("db-password", new Resolved("the-password")));
      assertThat(secretPathsRead(http))
          .containsExactlyInAnyOrder("/secrets/Db-Password/", "/secrets/db-password/");
    }
  }

  @Test
  void shouldReturnTheValueByteForByte() {
    // given
    final var pem =
        "-----BEGIN PRIVATE KEY-----\nMIIEvQIBADANBgkqhkiG9w0BAQEFAASC\n-----END PRIVATE KEY-----\n";
    final var json = "{\"user\": \"admin\", \"pass\": \"p\\\\a\\\"ss\"}";
    final var unicode = "tokén-密码-🔑";
    final var http =
        signedIn().putSecret("pem", pem).putSecret("json", json).putSecret("unicode", unicode);
    try (final var store = store(AzureKeyVaultStoreConfig.of(VAULT_URL), http)) {

      // when
      final var results = store.resolve(Set.of("pem", "json", "unicode"));

      // then
      assertThat(results)
          .containsOnly(
              entry("pem", new Resolved(pem)),
              entry("json", new Resolved(json)),
              entry("unicode", new Resolved(unicode)));
    }
  }

  @Test
  void shouldReportAnEmptyValueAsNotFound() {
    // given
    final var http = signedIn().putSecret("api-key", "");
    try (final var store = store(AzureKeyVaultStoreConfig.of(VAULT_URL), http)) {

      // when
      final var result = store.resolve(Set.of("api-key")).get("api-key");

      // then
      assertThat(failure(result).code()).isEqualTo(NOT_FOUND);
      assertThat(failure(result).message()).contains("api-key", "empty");
    }
  }

  @Test
  void shouldResolveEveryNameOnItsOwnWithoutLeakingAValue() {
    // given
    final var http = signedIn().putSecret("user", "the-user").putSecret("password", "the-password");
    try (final var log = LogCapturer.capturing(OneByOneSecretResolver.class, Level.TRACE);
        final var store = store(AzureKeyVaultStoreConfig.of(VAULT_URL), http)) {

      // when
      final var results = store.resolve(Set.of("user", "password", "missing"));

      // then — the missing name fails alone
      assertThat(results)
          .containsEntry("user", new Resolved("the-user"))
          .containsEntry("password", new Resolved("the-password"));
      final var missing = failure(results.get("missing"));
      assertThat(missing.code()).isEqualTo(NOT_FOUND);
      assertThat(missing.message()).isEqualTo("Secret not found: missing");
      assertThat(String.valueOf(missing) + log.messages())
          .doesNotContain("the-user", "the-password");
    }
  }

  @Test
  void shouldKeepReadingTheVaultPastTheTokenLifetime() {
    // given — every token is already expired when it is issued, so each call outlives the last
    // token, as a cluster does that runs longer than one token lifetime
    final var http = StubAzureHttpClient.signingIn(0);
    try (final var store =
        AzureKeyVaultSecretStore.fromConfig(
            PHYSICAL_TENANT,
            STORE_ID,
            AzureKeyVaultStoreConfig.of(VAULT_URL),
            new SimpleMeterRegistry(),
            servicePrincipal(),
            http)) {
      final int signInsAtStartup = http.tokenRequests().size();

      // when
      final var first = store.client().getSecret("a");
      final var second = store.client().getSecret("a");

      // then — the vault client's bearer-token policy signed in again rather than failing
      assertThat(first.getValue()).isEqualTo("secret-value");
      assertThat(second.getValue()).isEqualTo("secret-value");
      assertThat(http.tokenRequests()).hasSize(signInsAtStartup + 2);
    }
  }

  @Test
  void shouldShutDownTheCredentialExecutorOnClose() {
    // given
    final var store = store(AzureKeyVaultStoreConfig.of(VAULT_URL), signedIn());

    // when
    store.close();

    // then
    assertThat(store.credentialExecutor().isShutdown()).isTrue();
  }

  @Test
  void shouldCountAFailedRefreshWhileTheTokenIsStillValid() {
    // given a token that is still valid but inside the window the vault client refreshes it ahead
    // of its expiry
    final var http = StubAzureHttpClient.signingIn(120);
    final var meterRegistry = new SimpleMeterRegistry();
    try (final var store =
        AzureKeyVaultSecretStore.fromConfig(
            PHYSICAL_TENANT,
            STORE_ID,
            AzureKeyVaultStoreConfig.of(VAULT_URL),
            meterRegistry,
            servicePrincipal(),
            http)) {
      store.client().getSecret("a");

      // when every refresh is rejected
      http.rejectSignIn(true);

      // then — the client waits 30s after a token before it tries to refresh it, and from then on
      // keeps reading the vault with the old token, yet the failed refresh is counted
      Awaitility.await()
          .atMost(Duration.ofSeconds(60))
          .pollInterval(Duration.ofSeconds(1))
          .untilAsserted(
              () -> {
                assertThat(store.client().getSecret("a").getValue()).isEqualTo("secret-value");
                assertThat(
                        meterRegistry
                            .get(SecretStoreMetricsDoc.SIGNIN_FAILURES.getName())
                            .counter()
                            .count())
                    .isPositive();
              });
    }
  }

  @Test
  void shouldRemoveItsMeterOnClose() {
    // given
    final var meterRegistry = new SimpleMeterRegistry();
    final var store = store(AzureKeyVaultStoreConfig.of(VAULT_URL), meterRegistry, signedIn());
    assertThat(meterRegistry.find(SecretStoreMetricsDoc.SIGNIN_FAILURES.getName()).counter())
        .isNotNull();

    // when
    store.close();

    // then — a store rebuilt under the same ID registers a fresh series instead of a stale one
    assertThat(meterRegistry.find(SecretStoreMetricsDoc.SIGNIN_FAILURES.getName()).meters())
        .isEmpty();
  }

  @Test
  void shouldConfirmAtStartupThatItCanListTheVault() {
    // given
    final var http = signedIn();

    try (final var log = LogCapturer.capturing(StartupCheck.class, Level.INFO)) {
      // when
      try (final var store = store(AzureKeyVaultStoreConfig.of(VAULT_URL), http)) {

        // then — one listing, of names only, and one line saying so
        assertThat(http.requests())
            .filteredOn(request -> request.authorization() != null)
            .extracting(StubAzureHttpClient.Request::url)
            .singleElement()
            .asString()
            .startsWith(VAULT_URL + "/secrets?");
        assertThat(log.messages())
            .singleElement()
            .asString()
            .contains(STORE_ID, PHYSICAL_TENANT, VAULT_URL, "can list its secrets");
      }
    }
  }

  @Test
  void shouldReadOnlyTheContainerSecretAtStartupInContainerMode() {
    // given
    final var http = signedIn();

    try (final var log = LogCapturer.capturing(StartupCheck.class, Level.INFO)) {
      // when
      try (final var store = store(containerConfig(), http)) {

        // then — the one call this mode makes, so it needs nothing but 'get'
        assertThat(http.requests())
            .filteredOn(request -> request.authorization() != null)
            .extracting(StubAzureHttpClient.Request::url)
            .singleElement()
            .asString()
            .startsWith(VAULT_URL + "/secrets/camunda-container/?");
        assertThat(log.messages())
            .singleElement()
            .asString()
            .contains("can read its container secret 'camunda-container'");
      }
    }
  }

  @Test
  void shouldStartWithOneWarningNamingTheMissingRole() {
    // given an identity that signs in but has no role on the vault
    final var http = signedIn();
    http.answerVaultWith(403);

    try (final var checkLog = LogCapturer.capturing(StartupCheck.class, Level.WARN);
        final var signInLog = LogCapturer.capturing(SignInMonitor.class, Level.WARN)) {
      // when
      try (final var store = store(AzureKeyVaultStoreConfig.of(VAULT_URL), http)) {

        // then
        assertThat(store).isNotNull();
        assertThat(signInLog.messages()).isEmpty();
        assertThat(checkLog.messages())
            .singleElement()
            .asString()
            .contains(PHYSICAL_TENANT, VAULT_URL, "access denied", "'Key Vault Secrets User'");
      }
    }
  }

  @Test
  void shouldWarnAtStartupWhenTheVaultRejectsTheToken() {
    // given an identity that signs in, but to a tenant the vault does not trust
    final var http = signedIn();
    http.answerVaultWith(401);

    try (final var checkLog = LogCapturer.capturing(StartupCheck.class, Level.WARN);
        final var signInLog = LogCapturer.capturing(SignInMonitor.class, Level.WARN)) {
      // when
      try (final var store = store(AzureKeyVaultStoreConfig.of(VAULT_URL), http)) {

        // then
        assertThat(store).isNotNull();
        assertThat(signInLog.messages()).isEmpty();
        assertThat(checkLog.messages())
            .singleElement()
            .asString()
            .contains(PHYSICAL_TENANT, VAULT_URL, "rejected the identity's token");
      }
    }
  }

  @Test
  void shouldStartWithOneWarningNamingTheCredentialThatFailedToSignIn() {
    // given a service principal Entra ID rejects
    final var http = signedIn();
    http.rejectSignIn(true);

    try (final var checkLog = LogCapturer.capturing(StartupCheck.class, Level.WARN);
        final var log = LogCapturer.capturing(SignInMonitor.class, Level.WARN)) {
      // when
      try (final var store =
          AzureKeyVaultSecretStore.fromConfig(
              PHYSICAL_TENANT,
              STORE_ID,
              AzureKeyVaultStoreConfig.of(VAULT_URL),
              new SimpleMeterRegistry(),
              servicePrincipal(),
              http)) {

        // then
        assertThat(store).isNotNull();
        assertThat(checkLog.messages()).isEmpty();
        assertThat(log.messages())
            .singleElement()
            .asString()
            .contains(
                PHYSICAL_TENANT,
                VAULT_URL,
                "failed to sign in",
                AzureCredentialChain.ENVIRONMENT,
                "AADSTS7000215");
      }
    }
  }

  @Test
  void shouldStartWithinTheCallTimeoutWhenTheVaultNeverAnswers() throws IOException {
    // given a vault that accepts connections but never answers, reached over the real HTTP client;
    // the SDK's first, unauthenticated call to the vault hangs, so the store never signs in
    final var callTimeout = Duration.ofSeconds(1);
    try (final var vault = new SilentServer();
        final var log = LogCapturer.capturing(StartupCheck.class, Level.WARN)) {
      final var config =
          new AzureKeyVaultStoreConfig(
              "https://localhost:" + vault.port(),
              null,
              null,
              null,
              null,
              0,
              callTimeout,
              callTimeout);

      // when
      final long start = System.nanoTime();
      try (final var store = store(config, new SimpleMeterRegistry(), null)) {
        final var took = Duration.ofNanos(System.nanoTime() - start);

        // then
        assertThat(store).isNotNull();
        assertThat(took).isLessThan(callTimeout.plusSeconds(3));
        assertThat(log.messages())
            .singleElement()
            .asString()
            .contains(
                "no answer within the call timeout of PT1S, before signing in",
                "either the vault is unreachable");
      }
    }
  }

  private static AzureKeyVaultSecretStore store(
      final AzureKeyVaultStoreConfig config, final StubAzureHttpClient http) {
    return store(config, new SimpleMeterRegistry(), http);
  }

  /** Signs in with the managed identity the stub's IMDS answers for. */
  private static AzureKeyVaultSecretStore store(
      final AzureKeyVaultStoreConfig config,
      final MeterRegistry meterRegistry,
      final @Nullable HttpClient http) {
    return AzureKeyVaultSecretStore.fromConfig(
        PHYSICAL_TENANT,
        STORE_ID,
        config,
        meterRegistry,
        StubAzureHttpClient.environment(Map.of()),
        http);
  }

  private static StubAzureHttpClient signedIn() {
    return StubAzureHttpClient.signingIn(3600);
  }

  private static Failed failure(final @Nullable SecretResolutionResult result) {
    assertThat(result).isInstanceOf(Failed.class);
    return (Failed) result;
  }

  private static List<String> secretPathsRead(final StubAzureHttpClient http) {
    return http.requests().stream()
        .filter(request -> request.secretName() != null)
        .filter(request -> request.authorization() != null)
        .map(request -> request.original().getUrl().getPath())
        .toList();
  }

  private static AzureKeyVaultStoreConfig prefixed(final String pathPrefix) {
    return new AzureKeyVaultStoreConfig(
        VAULT_URL,
        pathPrefix,
        null,
        null,
        null,
        AzureKeyVaultStoreConfig.DEFAULT_MAX_RETRIES,
        AzureKeyVaultStoreConfig.DEFAULT_CALL_TIMEOUT,
        AzureKeyVaultStoreConfig.DEFAULT_ATTEMPT_TIMEOUT);
  }

  private static AzureKeyVaultStoreConfig containerConfig() {
    return new AzureKeyVaultStoreConfig(
        VAULT_URL,
        "camunda-",
        "container",
        null,
        null,
        AzureKeyVaultStoreConfig.DEFAULT_MAX_RETRIES,
        AzureKeyVaultStoreConfig.DEFAULT_CALL_TIMEOUT,
        AzureKeyVaultStoreConfig.DEFAULT_ATTEMPT_TIMEOUT);
  }

  private static Configuration servicePrincipal() {
    return StubAzureHttpClient.environment(
        Map.of(
            "AZURE_TENANT_ID", "tenant-id",
            "AZURE_CLIENT_ID", "sp-client-id",
            "AZURE_CLIENT_SECRET", "sp-secret"));
  }

  /** Accepts every connection and holds it open without ever reading or writing a byte. */
  private static final class SilentServer implements AutoCloseable {

    private final ServerSocket socket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
    private final List<Socket> connections = new CopyOnWriteArrayList<>();

    private SilentServer() throws IOException {
      Thread.ofPlatform()
          .daemon()
          .start(
              () -> {
                while (!socket.isClosed()) {
                  try {
                    connections.add(socket.accept());
                  } catch (final IOException e) {
                    return;
                  }
                }
              });
    }

    int port() {
      return socket.getLocalPort();
    }

    @Override
    public void close() throws IOException {
      socket.close();
      for (final var connection : connections) {
        connection.close();
      }
    }
  }
}
