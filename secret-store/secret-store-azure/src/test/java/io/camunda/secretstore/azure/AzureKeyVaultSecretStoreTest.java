/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.azure;

import static io.camunda.secretstore.SecretErrorCode.ACCESS_DENIED;
import static io.camunda.secretstore.SecretErrorCode.INVALID_REF;
import static io.camunda.secretstore.SecretErrorCode.NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.entry;

import com.azure.core.http.HttpClient;
import com.azure.core.util.Configuration;
import io.camunda.secretstore.SecretResolutionResult;
import io.camunda.secretstore.SecretResolutionResult.Failed;
import io.camunda.secretstore.SecretResolutionResult.Resolved;
import io.camunda.secretstore.SecretStoreMetricsDoc;
import io.camunda.secretstore.SecretStoreUnavailableException;
import io.camunda.zeebe.test.util.logging.LogCapturer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
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
  private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");
  private static final InstantSource CLOCK = InstantSource.fixed(NOW);

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
      assertThat(missing.message())
          .startsWith("Secret not found: missing (Key Vault answered HTTP 404 SecretNotFound: ");
      assertThat(String.valueOf(missing) + log.messages())
          .doesNotContain("the-user", "the-password");
    }
  }

  @Test
  void shouldRefuseANameKeyVaultCannotHoldWithoutCallingTheVault() {
    // given
    final var names = Set.of("API_KEY", "has.dot", "tokén", "a/b");
    final var http = signedIn().putSecret("api-key", "the-key");
    try (final var store = store(AzureKeyVaultStoreConfig.of(VAULT_URL), http)) {

      // when
      final var results = store.resolve(names);

      // then
      assertThat(results).containsOnlyKeys(names);
      results.forEach(
          (name, result) -> {
            assertThat(failure(result).code()).isEqualTo(INVALID_REF);
            assertThat(failure(result).message())
                .contains(
                    name, AzureKeyVaultSecretStore.NAME_PATTERN.pattern(), "container secret");
          });
      assertThat(secretPathsRead(http)).isEmpty();
    }
  }

  @Test
  void shouldRefuseANameThatExceedsTheLengthLimitWithThePrefix() {
    // given — "camunda-" leaves 119 characters for the name
    final var longest = "a".repeat(119);
    final var tooLong = "a".repeat(120);
    final var http = signedIn().putSecret("camunda-" + longest, "the-key");
    try (final var store = store(prefixed("camunda-"), http)) {

      // when
      final var results = store.resolve(Set.of(longest, tooLong));

      // then
      assertThat(results.get(longest)).isEqualTo(new Resolved("the-key"));
      assertThat(failure(results.get(tooLong)).code()).isEqualTo(INVALID_REF);
      assertThat(failure(results.get(tooLong)).message()).contains("'camunda-'");
    }
  }

  @Test
  void shouldRefuseAnExpiredSecretNamingItsExpiryDate() {
    // given — Key Vault still returns the value of an expired secret
    final var expiresOn = NOW.minusSeconds(60);
    final var http = signedIn().putSecret("api-key", "the-key", null, expiresOn);
    try (final var store = store(AzureKeyVaultStoreConfig.of(VAULT_URL), http, CLOCK)) {

      // when
      final var result = failure(store.resolve(Set.of("api-key")).get("api-key"));

      // then
      assertThat(result.code()).isEqualTo(NOT_FOUND);
      assertThat(result.message())
          .isEqualTo("Secret api-key expired on 2026-06-01T11:59:00Z and is refused");
    }
  }

  @Test
  void shouldRefuseASecretNotActiveYetNamingItsActivationDate() {
    // given
    final var notBefore = NOW.plusSeconds(3600);
    final var http = signedIn().putSecret("api-key", "the-key", notBefore, null);
    try (final var store = store(AzureKeyVaultStoreConfig.of(VAULT_URL), http, CLOCK)) {

      // when
      final var result = failure(store.resolve(Set.of("api-key")).get("api-key"));

      // then
      assertThat(result.code()).isEqualTo(NOT_FOUND);
      assertThat(result.message())
          .isEqualTo("Secret api-key is not active until 2026-06-01T13:00:00Z and is refused");
    }
  }

  @Test
  void shouldResolveASecretBetweenItsActivationAndExpiryDates() {
    // given
    final var http =
        signedIn().putSecret("api-key", "the-key", NOW.minusSeconds(1), NOW.plusSeconds(1));
    try (final var store = store(AzureKeyVaultStoreConfig.of(VAULT_URL), http, CLOCK)) {

      // when
      final var results = store.resolve(Set.of("api-key"));

      // then
      assertThat(results).containsExactly(entry("api-key", new Resolved("the-key")));
    }
  }

  @Test
  void shouldRefuseASecretAtTheInstantItExpires() {
    // given
    final var http = signedIn().putSecret("api-key", "the-key", null, NOW);
    try (final var store = store(AzureKeyVaultStoreConfig.of(VAULT_URL), http, CLOCK)) {

      // when
      final var result = failure(store.resolve(Set.of("api-key")).get("api-key"));

      // then
      assertThat(result.message())
          .isEqualTo("Secret api-key expired on 2026-06-01T12:00:00Z and is refused");
    }
  }

  @Test
  void shouldResolveASecretAtTheInstantItBecomesActive() {
    // given
    final var http = signedIn().putSecret("api-key", "the-key", NOW, null);
    try (final var store = store(AzureKeyVaultStoreConfig.of(VAULT_URL), http, CLOCK)) {

      // when
      final var results = store.resolve(Set.of("api-key"));

      // then
      assertThat(results).containsExactly(entry("api-key", new Resolved("the-key")));
    }
  }

  @Test
  void shouldRefuseADisabledSecretRatherThanReportAccessDenied() {
    // given
    final var http = signedIn().putDisabledSecret("api-key", "the-key");
    try (final var store = store(AzureKeyVaultStoreConfig.of(VAULT_URL), http)) {

      // when
      final var result = failure(store.resolve(Set.of("api-key")).get("api-key"));

      // then
      assertThat(result.code()).isEqualTo(NOT_FOUND);
      assertThat(result.message())
          .isEqualTo("Secret api-key is disabled in Azure Key Vault and is refused")
          .doesNotContainIgnoringCase("access denied");
    }
  }

  @Test
  void shouldRefuseDisabledExpiredAndInactiveSecretsAlikeWithoutLeakingTheirValues() {
    // given
    final var http =
        signedIn()
            .putDisabledSecret("disabled", "disabled-value")
            .putSecret("expired", "expired-value", null, NOW.minusSeconds(1))
            .putSecret("inactive", "inactive-value", NOW.plusSeconds(1), null);
    try (final var log = LogCapturer.capturing(OneByOneSecretResolver.class, Level.TRACE);
        final var store = store(AzureKeyVaultStoreConfig.of(VAULT_URL), http, CLOCK)) {

      // when
      final var results = store.resolve(Set.of("disabled", "expired", "inactive"));

      // then
      assertThat(results.values())
          .hasSize(3)
          .allSatisfy(result -> assertThat(failure(result).code()).isEqualTo(NOT_FOUND));
      assertThat(results.values().stream().map(AzureKeyVaultSecretStoreTest::describe))
          .allSatisfy(
              failure ->
                  assertThat(failure + log.messages())
                      .doesNotContain("disabled-value", "expired-value", "inactive-value"));
    }
  }

  @Test
  void shouldNotRetryAMissingOrDeniedSecret() {
    // given
    final var http = signedIn().putSecret("present", "the-value").answerNextSecretReadsWith(403);
    try (final var store = store(AzureKeyVaultStoreConfig.of(VAULT_URL), http)) {

      // when
      final var denied = failure(store.resolve(Set.of("denied")).get("denied"));
      final var missing = failure(store.resolve(Set.of("missing")).get("missing"));

      // then — the SDK retries neither, and the request names its secret once each
      assertThat(denied.code()).isEqualTo(ACCESS_DENIED);
      assertThat(missing.code()).isEqualTo(NOT_FOUND);
      assertThat(secretPathsRead(http)).containsExactly("/secrets/denied/", "/secrets/missing/");
    }
  }

  @Test
  void shouldReportAccessDeniedWithKeyVaultsAnswerAndWarnOnlyOnce() {
    // given an identity whose role on the vault was revoked after startup
    final var http = signedIn().putSecret("a", "value-a").answerNextSecretReadsWith(403, 403);
    try (final var log = LogCapturer.capturing(OneByOneSecretResolver.class, Level.WARN);
        final var store = store(AzureKeyVaultStoreConfig.of(VAULT_URL), http)) {

      // when
      final var first = failure(store.resolve(Set.of("a")).get("a"));
      final var second = failure(store.resolve(Set.of("a")).get("a"));

      // then
      assertThat(first.code()).isEqualTo(ACCESS_DENIED);
      assertThat(first.message())
          .isEqualTo(
              "Access denied for secret: a (Key Vault answered HTTP 403 Forbidden:"
                  + " Key Vault answered 403)");
      assertThat(second.code()).isEqualTo(ACCESS_DENIED);
      assertThat(log.messages())
          .singleElement()
          .asString()
          .contains(STORE_ID, PHYSICAL_TENANT, VAULT_URL, "'a'", "'Key Vault Secrets User'")
          .doesNotContain("value-a");
    }
  }

  @Test
  void shouldResolveAfterATransientFailureIsRetried() {
    // given
    final var http = signedIn().putSecret("a", "value-a").answerNextSecretReadsWith(503);
    try (final var store = store(withRetries(1, Duration.ofSeconds(30)), http)) {

      // when
      final var results = store.resolve(Set.of("a"));

      // then
      assertThat(results).containsExactly(entry("a", new Resolved("value-a")));
      assertThat(secretPathsRead(http)).hasSize(2);
    }
  }

  @Test
  void shouldReportTheStoreUnavailableOnceRetriesAreExhausted() {
    // given
    final var http = signedIn().putSecret("a", "value-a").answerNextSecretReadsWith(503, 503, 503);
    try (final var store = store(withRetries(1, Duration.ofSeconds(30)), http)) {

      // when / then
      assertThatThrownBy(() -> store.resolve(Set.of("a")))
          .isInstanceOf(SecretStoreUnavailableException.class)
          .hasMessage(
              "Azure Key Vault is unavailable: Key Vault answered HTTP 503 Error:"
                  + " Key Vault answered 503");
      assertThat(secretPathsRead(http)).hasSize(2);
    }
  }

  @Test
  void shouldGiveUpAtTheCallTimeoutRatherThanWaitForALongRetryAfter() {
    // given
    final var callTimeout = Duration.ofSeconds(1);
    final var http =
        signedIn().putSecret("a", "value-a").answerNextSecretReadWith(429, Duration.ofMinutes(5));
    try (final var store = store(withRetries(3, callTimeout), http)) {

      // when
      final long start = System.nanoTime();
      assertThatThrownBy(() -> store.resolve(Set.of("a")))
          // then
          .isInstanceOf(SecretStoreUnavailableException.class)
          .hasMessageContaining("within the call timeout of PT1S");
      assertThat(Duration.ofNanos(System.nanoTime() - start))
          .isLessThan(callTimeout.plusSeconds(3));
      assertThat(secretPathsRead(http)).hasSize(1);
    }
  }

  @Test
  void shouldReportAVaultThatKeepsFailingWithItsAnswerRatherThanAsATimeout() {
    // given — with the SDK's own backoff, three retries would wait 5.6s, past the call timeout
    final var http =
        signedIn().putSecret("a", "value-a").answerNextSecretReadsWith(503, 503, 503, 503);
    try (final var store =
        store(
            withRetries(AzureKeyVaultStoreConfig.DEFAULT_MAX_RETRIES, Duration.ofSeconds(1)),
            http)) {

      // when / then
      assertThatThrownBy(() -> store.resolve(Set.of("a")))
          .isInstanceOf(SecretStoreUnavailableException.class)
          .hasMessageContaining("Key Vault answered HTTP 503");
      assertThat(secretPathsRead(http)).hasSize(1 + AzureKeyVaultStoreConfig.DEFAULT_MAX_RETRIES);
    }
  }

  @Test
  void shouldGiveUpAtTheCallTimeoutWhenTheVaultHangs() {
    // given
    final var callTimeout = Duration.ofSeconds(1);
    final var http = signedIn();
    try (final var store = store(withRetries(0, callTimeout), http)) {
      http.hangSecretReads();

      // when
      final long start = System.nanoTime();
      assertThatThrownBy(() -> store.resolve(Set.of("a")))
          // then
          .isInstanceOf(SecretStoreUnavailableException.class)
          .hasMessage(
              "Azure Key Vault is unavailable: it did not resolve 1 secret references within"
                  + " the call timeout of PT1S");
      assertThat(Duration.ofNanos(System.nanoTime() - start))
          .isLessThan(callTimeout.plusSeconds(3));
    }
  }

  @Test
  void shouldReportTheStoreUnavailableWhenItCannotSignIn() {
    // given no route to Entra ID or IMDS: the sign-in fails without an HTTP answer
    try (final var store =
        store(withRetries(0, Duration.ofSeconds(5)), StubAzureHttpClient.offline())) {

      // when / then — reported as the store's failure, not as one secret's
      assertThatThrownBy(() -> store.resolve(Set.of("a")))
          .isInstanceOf(SecretStoreUnavailableException.class)
          .hasMessageStartingWith("Azure Key Vault is unavailable: ")
          .hasMessageNotContaining("call timeout")
          .hasMessageNotContaining("NullPointerException");
    }
  }

  @Test
  void shouldReportAccessDeniedPerSecretAfterAFailedStartupCheck() {
    // given an identity without a role, which the startup check only warned about
    final var http = signedIn();
    http.answerVaultWith(403);
    try (final var store = store(AzureKeyVaultStoreConfig.of(VAULT_URL), http)) {

      // when
      final var result = failure(store.resolve(Set.of("a")).get("a"));

      // then
      assertThat(result.code()).isEqualTo(ACCESS_DENIED);
      assertThat(result.message())
          .startsWith("Access denied for secret: a (Key Vault answered HTTP 403");
      // the SDK's exception holds the request, bearer token included
      assertThat(result.cause()).isNull();
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
  void shouldReportTheStoreUnavailableOnceClosed() {
    // given
    final var store = store(AzureKeyVaultStoreConfig.of(VAULT_URL), signedIn());

    // when
    store.close();

    // then
    assertThatThrownBy(() -> store.resolve(Set.of("a")))
        .isInstanceOf(SecretStoreUnavailableException.class)
        .hasMessage("Azure Key Vault store is closed: cannot resolve 1 secret references");
    assertThatThrownBy(store::list)
        .isInstanceOf(SecretStoreUnavailableException.class)
        .hasMessage("Azure Key Vault store is closed: cannot list its secrets");
  }

  @Test
  void shouldReportTheStoreUnavailableAndKeepTheInterruptWhenTheCallerIsInterrupted() {
    // given
    try (final var store = store(AzureKeyVaultStoreConfig.of(VAULT_URL), signedIn())) {
      Thread.currentThread().interrupt();

      // when
      final var thrown = catchThrowable(() -> store.resolve(Set.of("a")));

      // then — Thread.interrupted() also clears the flag for the next test
      assertThat(Thread.interrupted()).isTrue();
      assertThat(thrown)
          .isInstanceOf(SecretStoreUnavailableException.class)
          .hasMessage(
              "Interrupted while waiting for Azure Key Vault to resolve 1 secret references");
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

  private static AzureKeyVaultSecretStore store(
      final AzureKeyVaultStoreConfig config,
      final StubAzureHttpClient http,
      final InstantSource clock) {
    return AzureKeyVaultSecretStore.fromConfig(
        PHYSICAL_TENANT,
        STORE_ID,
        config,
        new SimpleMeterRegistry(),
        StubAzureHttpClient.environment(Map.of()),
        http,
        clock);
  }

  /** A failure as an operator could see it: its message and every cause's message. */
  private static String describe(final SecretResolutionResult result) {
    final var failure = failure(result);
    final var description = new StringBuilder(failure.message());
    for (Throwable cause = failure.cause(); cause != null; cause = cause.getCause()) {
      description.append('\n').append(cause);
    }
    return description.toString();
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
    return config(
        pathPrefix,
        null,
        AzureKeyVaultStoreConfig.DEFAULT_MAX_RETRIES,
        AzureKeyVaultStoreConfig.DEFAULT_CALL_TIMEOUT);
  }

  private static AzureKeyVaultStoreConfig withRetries(
      final int maxRetries, final Duration callTimeout) {
    return config(null, null, maxRetries, callTimeout);
  }

  private static AzureKeyVaultStoreConfig containerConfig() {
    return config(
        "camunda-",
        "container",
        AzureKeyVaultStoreConfig.DEFAULT_MAX_RETRIES,
        AzureKeyVaultStoreConfig.DEFAULT_CALL_TIMEOUT);
  }

  /** The attempt timeout is the default, shortened to the call timeout when that is shorter. */
  private static AzureKeyVaultStoreConfig config(
      final @Nullable String pathPrefix,
      final @Nullable String containerSecretId,
      final int maxRetries,
      final Duration callTimeout) {
    final var defaultAttempt = AzureKeyVaultStoreConfig.DEFAULT_ATTEMPT_TIMEOUT;
    return new AzureKeyVaultStoreConfig(
        VAULT_URL,
        pathPrefix,
        containerSecretId,
        null,
        null,
        maxRetries,
        callTimeout,
        callTimeout.compareTo(defaultAttempt) < 0 ? callTimeout : defaultAttempt);
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
