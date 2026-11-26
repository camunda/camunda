/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.azure;

import static io.camunda.secretstore.azure.StubAzureHttpClient.environment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.secretstore.SecretResolutionResult.Resolved;
import io.camunda.secretstore.SecretStoreUnavailableException;
import io.camunda.zeebe.test.util.logging.LogCapturer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.logging.log4j.Level;
import org.awaitility.Awaitility;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Signs the store in with workload and managed identity over real HTTPS, against a mocked Entra ID,
 * IMDS and Key Vault ({@link MockAzure}): the SDK's own HTTP client, TLS, wire format and Key Vault
 * challenge all run as in production, with no Azure subscription.
 *
 * <p>MSAL caches managed identity tokens process-wide, per client ID, and no test can clear that
 * cache. So every test uses its own tenant, client IDs and tokens, except the system-assigned one:
 * that identity has a single cache entry per JVM, see {@link #SYSTEM_ASSIGNED_TOKEN}.
 *
 * <p>MSAL also picks where to get a managed identity token from the process environment, not from
 * the store's: on a host that sets {@link #MANAGED_IDENTITY_SOURCES}, it would skip the mocked
 * IMDS, so the tests don't run there.
 */
final class AzureMockedEntraSignInIT {

  private static final String AUTHORITY = "https://" + RoutedHttpClient.AUTHORITY_HOST;
  private static final List<String> SDK_AND_STORE_LOGGERS =
      List.of("com.azure", "com.microsoft.aad", "io.camunda.secretstore");
  private static final List<String> MANAGED_IDENTITY_SOURCES =
      List.of("IDENTITY_ENDPOINT", "IDENTITY_HEADER", "MSI_ENDPOINT", "IMDS_ENDPOINT");

  /**
   * The one token the system-assigned identity is issued in this JVM. A rerun of that test in the
   * same JVM, as CI does with a failed test, gets MSAL's cached token instead of asking the mock,
   * and the vault must still accept it.
   */
  private static final String SYSTEM_ASSIGNED_TOKEN = "token-system-" + UUID.randomUUID();

  @TempDir private static Path certificateDirectory;
  private static MockAzure azure;

  @TempDir private Path tempDir;

  private final String tenantId = UUID.randomUUID().toString();
  private final String clientId = UUID.randomUUID().toString();
  private final String token = "token-" + UUID.randomUUID();
  private final String vaultHost = "v" + shortId() + RoutedHttpClient.VAULT_DOMAIN;
  private final String secretName = "db-password-" + shortId();
  private final String secretValue = "value-" + UUID.randomUUID();

  @BeforeAll
  static void startMockAzure() throws Exception {
    assumeTrue(
        MANAGED_IDENTITY_SOURCES.stream().allMatch(variable -> System.getenv(variable) == null),
        () ->
            "MSAL would get managed identity tokens from this host, not the mock: one of "
                + MANAGED_IDENTITY_SOURCES
                + " is set");
    azure = MockAzure.start(certificateDirectory);
  }

  @AfterAll
  static void stopMockAzure() {
    if (azure != null) {
      azure.close();
    }
  }

  @AfterEach
  void resetMockAzure() {
    azure.reset();
  }

  @Test
  void shouldResolveASecretWithWorkloadIdentityThroughTheConfiguredAuthority() throws Exception {
    // given — the federated token AKS mounts into a pod labelled for workload identity
    final var assertion = "header." + UUID.randomUUID() + ".signature";
    final var tokenFile = Files.writeString(tempDir.resolve("azure-identity-token"), assertion);
    final var environment =
        Map.of(
            "AZURE_TENANT_ID", tenantId,
            "AZURE_CLIENT_ID", clientId,
            "AZURE_FEDERATED_TOKEN_FILE", tokenFile.toString());
    azure.entraIssues(tenantId, clientId, token, 3600);
    azure.vaultHolds(vaultHost, tenantId, token, secretName, secretValue);

    try (final var signInLog = capturingSignInLog();
        final var everyLog = capturingSdkAndStoreLogs()) {
      // when
      try (final var store = store(config(null), environment)) {
        final var results = store.resolve(Set.of(secretName));

        // then
        assertThat(results).containsEntry(secretName, new Resolved(secretValue));
      }

      // then — signed in once at Entra ID, as the pod's identity, through the configured authority
      assertThat(azure.tokenRequests())
          .singleElement()
          .satisfies(
              request -> {
                assertThat(request.getUrl()).startsWith("/" + tenantId + "/oauth2/v2.0/token");
                assertThat(request.getHeader(RoutedHttpClient.ORIGINAL_HOST))
                    .isEqualTo(RoutedHttpClient.AUTHORITY_HOST);
                assertThat(request.getBodyAsString())
                    .contains("client_id=" + clientId)
                    .contains("client_assertion=" + assertion)
                    .contains("grant_type=client_credentials");
              });
      assertVaultChallengedThenAccepted();
      assertThat(signInLog.messagesAt(Level.INFO))
          .singleElement()
          .asString()
          .contains("signed in with WorkloadIdentityCredential")
          .contains(clientId);
      assertNoSecretLogged(everyLog, assertion);
    }
  }

  @Test
  void shouldResolveASecretWithTheUserAssignedManagedIdentity() {
    // given — no AZURE_* variables: only IMDS knows the identity
    azure.imdsIssues(clientId, token, 3600);
    azure.vaultHolds(vaultHost, tenantId, token, secretName, secretValue);

    try (final var signInLog = capturingSignInLog();
        final var everyLog = capturingSdkAndStoreLogs()) {
      // when
      try (final var store = store(config(clientId), Map.of())) {
        final var results = store.resolve(Set.of(secretName));

        // then
        assertThat(results).containsEntry(secretName, new Resolved(secretValue));
      }

      // then — asked IMDS for the configured identity's token for Key Vault
      assertThat(azure.tokenRequests())
          .singleElement()
          .satisfies(
              request -> {
                assertThat(request.getHeader(RoutedHttpClient.ORIGINAL_HOST))
                    .isEqualTo(RoutedHttpClient.IMDS_HOST);
                assertThat(request.queryParameter("client_id").firstValue()).isEqualTo(clientId);
                assertThat(request.queryParameter("resource").firstValue())
                    .isEqualTo(MockAzure.VAULT_RESOURCE);
              });
      assertVaultChallengedThenAccepted();
      assertThat(signInLog.messagesAt(Level.INFO))
          .singleElement()
          .asString()
          .contains("signed in with ManagedIdentityCredential (user-assigned identity")
          .contains(clientId);
      assertNoSecretLogged(everyLog);
    }
  }

  @Test
  void shouldResolveASecretWithTheSystemAssignedManagedIdentity() {
    // given
    azure.imdsIssues(null, SYSTEM_ASSIGNED_TOKEN, 3600);
    azure.vaultHolds(vaultHost, tenantId, SYSTEM_ASSIGNED_TOKEN, secretName, secretValue);

    // when
    try (final var store = store(config(null), Map.of())) {
      final var results = store.resolve(Set.of(secretName));

      // then
      assertThat(results).containsEntry(secretName, new Resolved(secretValue));
    }

    // then — asked IMDS for the machine's own identity's token for Key Vault; none on a rerun
    // served from MSAL's cache
    assertThat(azure.tokenRequests())
        .hasSizeLessThanOrEqualTo(1)
        .allSatisfy(
            request -> {
              assertThat(request.getHeader(RoutedHttpClient.ORIGINAL_HOST))
                  .isEqualTo(RoutedHttpClient.IMDS_HOST);
              assertThat(request.queryParameter("client_id").isPresent()).isFalse();
              assertThat(request.queryParameter("resource").firstValue())
                  .isEqualTo(MockAzure.VAULT_RESOURCE);
            });
  }

  @Test
  void shouldReportTheStoreUnavailableWhenOnlyAUserLoginIsAvailable() {
    // given — a person's login in the environment, which DefaultAzureCredential would sign in
    // with, Entra ID ready to issue a token for it, and a machine without a managed identity
    final var username = "developer-" + shortId() + "@example.com";
    final var password = "password-" + UUID.randomUUID();
    final var environment =
        Map.of(
            "AZURE_TENANT_ID",
            tenantId,
            "AZURE_CLIENT_ID",
            clientId,
            "AZURE_USERNAME",
            username,
            "AZURE_PASSWORD",
            password);
    azure.entraIssues(tenantId, clientId, token, 3600);
    azure.imdsHasNoIdentity();
    azure.vaultHolds(vaultHost, tenantId, token, secretName, secretValue);

    // a user-assigned identity of its own, so no managed identity token another test left in
    // MSAL's process-wide cache can sign this store in
    try (final var everyLog = capturingSdkAndStoreLogs()) {
      try (final var store = store(config(UUID.randomUUID().toString()), environment)) {
        // when / then
        assertThatThrownBy(() -> store.resolve(Set.of(secretName)))
            .isInstanceOf(SecretStoreUnavailableException.class);
      }
      assertNoSecretLogged(everyLog, username, password);
    }

    // then — only IMDS was asked; the user's password never went to Entra ID
    assertThat(azure.tokenRequests())
        .isNotEmpty()
        .allSatisfy(
            request ->
                assertThat(request.getHeader(RoutedHttpClient.ORIGINAL_HOST))
                    .isEqualTo(RoutedHttpClient.IMDS_HOST));
    assertThat(azure.requestsTo(vaultHost))
        .allSatisfy(request -> assertThat(request.containsHeader("Authorization")).isFalse());
  }

  @Test
  void shouldSignEachPhysicalTenantInAsItsOwnIdentityAndDenyOnlyTheRefusedOne() {
    // given — two physical tenants with their own user-assigned identity and vault; tenant A's
    // identity has no role on its vault
    final var clientIdA = UUID.randomUUID().toString();
    final var clientIdB = UUID.randomUUID().toString();
    final var tokenA = "token-a-" + UUID.randomUUID();
    final var tokenB = "token-b-" + UUID.randomUUID();
    final var vaultA = "a" + shortId() + RoutedHttpClient.VAULT_DOMAIN;
    final var vaultB = "b" + shortId() + RoutedHttpClient.VAULT_DOMAIN;
    azure.imdsIssues(clientIdA, tokenA, 3600);
    azure.imdsIssues(clientIdB, tokenB, 3600);
    azure.vaultDenies(vaultA, tenantId, tokenA);
    azure.vaultHolds(vaultB, tenantId, tokenB, secretName, secretValue);

    try (final var everyLog = capturingSdkAndStoreLogs()) {
      try (final var storeA = store(config(vaultA, clientIdA), Map.of());
          final var storeB = store(config(vaultB, clientIdB), Map.of())) {
        // when / then — tenant B resolves while tenant A is refused.
        // #68640 maps the 403 to ACCESS_DENIED; until then it reports the store unavailable.
        assertThatThrownBy(() -> storeA.resolve(Set.of(secretName)))
            .isInstanceOf(SecretStoreUnavailableException.class)
            .hasMessageContaining("403");
        assertThat(storeB.resolve(Set.of(secretName)))
            .containsEntry(secretName, new Resolved(secretValue));
      }
      assertNoSecretLogged(everyLog, tokenA, tokenB);
    }

    // then — each tenant asked IMDS for its own identity, and sent only its own token
    assertThat(azure.tokenRequests())
        .extracting(request -> request.queryParameter("client_id").firstValue())
        .containsOnly(clientIdA, clientIdB)
        .contains(clientIdA, clientIdB);
    assertThat(azure.requestsTo(vaultA))
        .extracting(request -> request.getHeader("Authorization"))
        .containsOnly(null, "Bearer " + tokenA);
    assertThat(azure.requestsTo(vaultB))
        .extracting(request -> request.getHeader("Authorization"))
        .containsOnly(null, "Bearer " + tokenB);
  }

  @Test
  void shouldKeepResolvingWithWorkloadIdentityPastTheTokenLifetime() throws Exception {
    // given — Entra ID issues tokens that expire after 2 seconds
    final var tokenFile =
        Files.writeString(tempDir.resolve("azure-identity-token"), "header.payload.signature");
    final var environment =
        Map.of(
            "AZURE_TENANT_ID", tenantId,
            "AZURE_CLIENT_ID", clientId,
            "AZURE_FEDERATED_TOKEN_FILE", tokenFile.toString());
    azure.entraIssuesShortLivedTokens(tenantId, clientId, token, 2);
    azure.vaultHoldsForUnexpiredTokens(vaultHost, tenantId, token, secretName, secretValue);

    try (final var store = store(config(null), environment)) {
      // when / then
      assertKeepsResolvingPastTheTokenLifetime(store);
    }
  }

  @Test
  void shouldKeepResolvingWithManagedIdentityPastTheTokenLifetime() {
    // given — IMDS issues tokens that expire after 2 seconds
    azure.imdsIssuesShortLivedTokens(clientId, token, 2);
    azure.vaultHoldsForUnexpiredTokens(vaultHost, tenantId, token, secretName, secretValue);

    try (final var store = store(config(clientId), Map.of())) {
      // when / then
      assertKeepsResolvingPastTheTokenLifetime(store);
    }
  }

  /**
   * Resolves continuously for longer than two token lifetimes, while capturing every log. The first
   * failed resolution fails the test, as does any expired token reaching the vault: the vault
   * answers it with 401, which fails that resolution today, and is asserted on directly so the test
   * doesn't rely on that. The vault must also have accepted more than one token.
   */
  private void assertKeepsResolvingPastTheTokenLifetime(final AzureKeyVaultSecretStore store) {
    try (final var everyLog = capturingSdkAndStoreLogs()) {
      Awaitility.await()
          .during(Duration.ofSeconds(5))
          .atMost(Duration.ofSeconds(20))
          .pollInterval(Duration.ofMillis(200))
          .until(
              () -> {
                // thrown rather than retried, so no failed resolution goes unnoticed
                assertThat(store.resolve(Set.of(secretName)))
                    .containsEntry(secretName, new Resolved(secretValue));
                return true;
              });
      assertThat(azure.acceptedTokens(vaultHost).stream().distinct())
          .as("tokens the vault accepted")
          .hasSizeGreaterThanOrEqualTo(2);
      assertThat(azure.rejectedTokens(vaultHost)).as("expired tokens sent to the vault").isEmpty();
      // every issued token starts with the token field, so it covers them all
      assertNoSecretLogged(everyLog);
    }
  }

  private void assertVaultChallengedThenAccepted() {
    final List<LoggedRequest> vaultRequests = azure.requestsTo(vaultHost);
    assertThat(vaultRequests).hasSizeGreaterThanOrEqualTo(2);
    assertThat(vaultRequests.getFirst().containsHeader("Authorization")).isFalse();
    assertThat(vaultRequests.subList(1, vaultRequests.size()))
        .extracting(request -> request.getHeader("Authorization"))
        .containsOnly("Bearer " + token);
  }

  private void assertNoSecretLogged(final CapturedLogs logs, final String... moreSecrets) {
    final var secrets = new ArrayList<>(List.of(token, secretValue));
    secrets.addAll(List.of(moreSecrets));
    // each logger must have logged something, or this would pass for a logger never captured
    assertThat(logs.capturers()).allSatisfy(log -> assertThat(log.messages()).isNotEmpty());
    assertThat(secrets)
        .allSatisfy(
            secret ->
                assertThat(logs.capturers())
                    .as("logs or logged exceptions containing a secret")
                    .noneMatch(log -> log.containsInMessagesOrThrowables(secret)));
  }

  /**
   * Captures {@link SignInMonitor} at DEBUG, not INFO: a capturer sets its logger's level, and at
   * INFO it would hide SignInMonitor's DEBUG lines from {@link #capturingSdkAndStoreLogs()} too.
   */
  private static LogCapturer capturingSignInLog() {
    return LogCapturer.capturing(SignInMonitor.class, Level.DEBUG);
  }

  private static CapturedLogs capturingSdkAndStoreLogs() {
    return new CapturedLogs(
        SDK_AND_STORE_LOGGERS.stream()
            .map(logger -> LogCapturer.capturing(logger, Level.DEBUG))
            .toList());
  }

  private AzureKeyVaultSecretStore store(
      final AzureKeyVaultStoreConfig config, final Map<String, String> variables) {
    return AzureKeyVaultSecretStore.fromConfig(
        "pt-" + shortId(),
        "default",
        config,
        new SimpleMeterRegistry(),
        environment(variables),
        azure.httpClient());
  }

  private AzureKeyVaultStoreConfig config(final @Nullable String managedIdentityClientId) {
    return config(vaultHost, managedIdentityClientId);
  }

  private static AzureKeyVaultStoreConfig config(
      final String vault, final @Nullable String managedIdentityClientId) {
    return new AzureKeyVaultStoreConfig(
        "https://" + vault,
        null,
        null,
        managedIdentityClientId,
        AUTHORITY,
        AzureKeyVaultStoreConfig.DEFAULT_MAX_RETRIES,
        AzureKeyVaultStoreConfig.DEFAULT_CALL_TIMEOUT,
        AzureKeyVaultStoreConfig.DEFAULT_ATTEMPT_TIMEOUT);
  }

  private static String shortId() {
    return UUID.randomUUID().toString().substring(0, 8);
  }

  /** Several {@link LogCapturer}s, closed together. */
  private record CapturedLogs(List<LogCapturer> capturers) implements AutoCloseable {

    @Override
    public void close() {
      capturers.forEach(LogCapturer::close);
    }
  }
}
