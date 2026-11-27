/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.azure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.azure.core.credential.AccessToken;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import io.camunda.secretstore.SecretResolutionResult.Resolved;
import io.camunda.secretstore.SecretStoreUnavailableException;
import io.camunda.zeebe.test.util.logging.LogCapturer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import org.apache.logging.log4j.Level;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import reactor.core.publisher.Mono;

/**
 * Listing in one-by-one mode, over the stubbed Key Vault list API ({@link StubAzureHttpClient}),
 * which pages its answers with {@code nextLink} as Key Vault does.
 */
class AzureKeyVaultSecretListingTest {

  private static final String VAULT_URL = "https://my-vault.vault.azure.net";
  private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");
  private static final String STORE =
      "Azure Key Vault store 'default' of physical tenant 'pt1' at '" + VAULT_URL + "'";

  @Test
  void shouldListOnlyTheSecretsUnderThePathPrefixWithoutIt() {
    // given
    final var http =
        signedIn()
            .putSecret("camunda-a", "value-a")
            .putSecret("camunda-b", "value-b")
            .putSecret("other", "other-value");
    try (final var store = store(config("camunda-", Duration.ofSeconds(30)), http)) {

      // when
      final var names = store.list();

      // then
      assertThat(names).containsExactlyInAnyOrder("a", "b");
    }
  }

  @Test
  void shouldListEverySecretWithoutAPathPrefix() {
    // given
    final var http = signedIn().putSecret("camunda-a", "value-a").putSecret("other", "value-o");
    try (final var store = store(AzureKeyVaultStoreConfig.of(VAULT_URL), http)) {

      // when
      final var names = store.list();

      // then
      assertThat(names).containsExactlyInAnyOrder("camunda-a", "other");
    }
  }

  @Test
  void shouldMatchThePathPrefixIgnoringCaseAsKeyVaultResolvesIt() {
    // given
    final var http = signedIn().putSecret("Camunda-C", "value-c");
    try (final var store = store(config("camunda-", Duration.ofSeconds(30)), http)) {

      // when
      final var names = store.list();

      // then — the listed name resolves as listed
      assertThat(names).containsExactly("C");
      assertThat(store.resolve(Set.of("C"))).containsEntry("C", new Resolved("value-c"));
    }
  }

  @Test
  void shouldListDisabledExpiredAndNotYetActiveSecrets() {
    // given
    final var http =
        signedIn()
            .putSecret("camunda-active", "active-value")
            .putDisabledSecret("camunda-disabled")
            .putSecret("camunda-expired", "expired-value", null, NOW)
            .putSecret("camunda-inactive", "inactive-value", NOW.plusSeconds(1), null);
    try (final var store = store(config("camunda-", Duration.ofSeconds(30)), http)) {

      // when
      final var names = store.list();

      // then
      assertThat(names).containsExactlyInAnyOrder("active", "disabled", "expired", "inactive");
    }
  }

  @Test
  void shouldKeepEachListedSecretsStateProperties() {
    // given
    final var http =
        signedIn()
            .putSecret("camunda-active", "active-value")
            .putDisabledSecret("camunda-disabled")
            .putSecret("camunda-expired", "expired-value", null, NOW)
            .putSecret("camunda-inactive", "inactive-value", NOW.plusSeconds(1), null);
    final var resolver = resolver("camunda-", http);

    // when
    final var listed = resolver.listSecrets();

    // then — what a status is derived from later, without reading the secret again
    assertThat(listed)
        .extracting(
            ListedSecret::name,
            ListedSecret::enabled,
            ListedSecret::expiresOn,
            ListedSecret::notBefore)
        .containsExactlyInAnyOrder(
            tuple("active", true, null, null),
            tuple("disabled", false, null, null),
            tuple("expired", true, utc(NOW), null),
            tuple("inactive", true, null, utc(NOW.plusSeconds(1))));
  }

  @Test
  void shouldReadEveryPage() {
    // given more secrets than one page holds
    final var http = signedIn().listingPagesOf(2);
    for (final var name : new String[] {"a", "b", "c", "d", "e"}) {
      http.putSecret("camunda-" + name, "value-" + name);
    }
    try (final var store = store(config("camunda-", Duration.ofSeconds(30)), http)) {
      final int listRequestsBefore = listRequests(http);

      // when
      final var names = store.list();

      // then
      assertThat(names).containsExactlyInAnyOrder("a", "b", "c", "d", "e");
      assertThat(listRequests(http) - listRequestsBefore).isEqualTo(3);
    }
  }

  @Test
  void shouldListSecretsBackingACertificate() {
    // given
    final var http = signedIn().putCertificateSecret("camunda-tls-cert", "cert-value");
    try (final var store = store(config("camunda-", Duration.ofSeconds(30)), http)) {

      // when
      final var names = store.list();

      // then
      assertThat(names).containsExactly("tls-cert");
    }
  }

  @Test
  void shouldReportUnavailableAndNameTheListPermissionWhenListingIsDenied() {
    // given an identity allowed to read secrets but not to list them
    final var http = signedIn().putSecret("camunda-a", "value-a");
    try (final var store = store(config("camunda-", Duration.ofSeconds(30)), http)) {
      http.answerVaultWith(403);

      // when / then
      assertThatThrownBy(store::list)
          .isInstanceOf(SecretStoreUnavailableException.class)
          .hasMessage(
              STORE
                  + " is unavailable to list secrets: Key Vault answered HTTP 403 Forbidden: Key"
                  + " Vault answered 403. To list secrets, grant the identity the 'Key Vault"
                  + " Secrets User' role on the vault, or, if the vault uses access policies, the"
                  + " 'list' secret permission");
    }
  }

  @Test
  void shouldReportAnyOtherListingFailureAsUnavailableWithKeyVaultsAnswer() {
    // given
    final var http = signedIn().putSecret("camunda-a", "value-a");
    try (final var store = store(config("camunda-", 0, Duration.ofSeconds(30)), http)) {
      http.answerVaultWith(500);

      // when / then
      assertThatThrownBy(store::list)
          .isInstanceOf(SecretStoreUnavailableException.class)
          .hasMessage(
              STORE
                  + " is unavailable to list secrets: Key Vault answered HTTP 500 Error: Key Vault"
                  + " answered 500");
    }
  }

  @Test
  void shouldNotBlameTheListPermissionWhenTheVaultsNetworkRulesRefuseTheCaller() {
    // given
    final var http = signedIn().putSecret("camunda-a", "value-a");
    try (final var store = store(config("camunda-", Duration.ofSeconds(30)), http)) {
      http.answerVaultWith(403, "ForbiddenByFirewall");

      // when / then
      assertThatThrownBy(store::list)
          .isInstanceOf(SecretStoreUnavailableException.class)
          .hasMessage(
              STORE
                  + " is unavailable to list secrets: Key Vault answered HTTP 403 Forbidden: Client"
                  + " address is not authorized");
    }
  }

  @Test
  @Timeout(30)
  void shouldReportUnavailableWithTheCauseWhenListingCannotSignIn() {
    // given no route to Entra ID or IMDS: the sign-in fails without an HTTP answer
    try (final var store =
        store(config("camunda-", 0, Duration.ofSeconds(5)), StubAzureHttpClient.offline())) {

      // when / then
      assertThatThrownBy(store::list)
          .isInstanceOf(SecretStoreUnavailableException.class)
          .hasMessageStartingWith(STORE + " is unavailable to list secrets: ")
          .hasMessageContaining("Connection refused")
          .hasMessageNotContaining("'list' secret permission");
    }
  }

  @Test
  @Timeout(30)
  void shouldFailListingAsUnavailableWithinTheCallTimeout() {
    // given
    final var http = signedIn().putSecret("camunda-a", "value-a");
    try (final var store = store(config("camunda-", 0, Duration.ofSeconds(1)), http)) {
      http.hangListing();

      // when / then
      assertThatThrownBy(store::list)
          .isInstanceOf(SecretStoreUnavailableException.class)
          .hasMessage(
              STORE
                  + ": Azure Key Vault is unavailable: it did not list its secrets within the call"
                  + " timeout of PT1S");
    }
  }

  @Test
  void shouldListWithoutReadingAnySecretValue() {
    // given
    final var http =
        signedIn()
            .putSecret("camunda-a", "value-a")
            .putDisabledSecret("camunda-disabled")
            .putCertificateSecret("camunda-cert", "cert-value");
    try (final var log = LogCapturer.capturing(OneByOneSecretResolver.class, Level.TRACE);
        final var store = store(config("camunda-", Duration.ofSeconds(30)), http)) {

      // when
      store.list();

      // then — only list requests reach the vault, which never carry a value
      assertThat(http.requests())
          .filteredOn(StubAzureHttpClient.Request::isAuthorizedVaultRequest)
          .isNotEmpty()
          .allSatisfy(request -> assertThat(request.isListRequest()).isTrue());
      assertThat(String.join("\n", log.messages())).doesNotContain("value-a", "cert-value");
    }
  }

  private static int listRequests(final StubAzureHttpClient http) {
    return (int)
        http.requests().stream()
            .filter(StubAzureHttpClient.Request::isAuthorizedVaultRequest)
            .filter(StubAzureHttpClient.Request::isListRequest)
            .count();
  }

  private static OffsetDateTime utc(final Instant instant) {
    return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
  }

  private static StubAzureHttpClient signedIn() {
    return StubAzureHttpClient.signingIn(3600);
  }

  private static AzureKeyVaultSecretStore store(
      final AzureKeyVaultStoreConfig config, final StubAzureHttpClient http) {
    return AzureKeyVaultSecretStore.fromConfig(
        "pt1",
        "default",
        config,
        new SimpleMeterRegistry(),
        StubAzureHttpClient.environment(Map.of()),
        http,
        InstantSource.fixed(NOW));
  }

  /** A resolver reading the stub directly, to see what it keeps of each listed secret. */
  private static OneByOneSecretResolver resolver(
      final String pathPrefix, final StubAzureHttpClient http) {
    final var client =
        new SecretClientBuilder()
            .vaultUrl(VAULT_URL)
            .credential(
                request ->
                    Mono.just(new AccessToken(StubAzureHttpClient.TOKEN, OffsetDateTime.MAX)))
            .httpClient(http)
            .buildClient();
    return new OneByOneSecretResolver(
        client,
        pathPrefix,
        InstantSource.fixed(NOW),
        "test store",
        new CallTimeout(ForkJoinPool.commonPool(), AzureKeyVaultStoreConfig.DEFAULT_CALL_TIMEOUT));
  }

  private static AzureKeyVaultStoreConfig config(
      final @Nullable String pathPrefix, final Duration callTimeout) {
    return config(pathPrefix, AzureKeyVaultStoreConfig.DEFAULT_MAX_RETRIES, callTimeout);
  }

  /** The attempt timeout is the default, shortened to the call timeout when that is shorter. */
  private static AzureKeyVaultStoreConfig config(
      final @Nullable String pathPrefix, final int maxRetries, final Duration callTimeout) {
    final var defaultAttempt = AzureKeyVaultStoreConfig.DEFAULT_ATTEMPT_TIMEOUT;
    return new AzureKeyVaultStoreConfig(
        VAULT_URL,
        pathPrefix,
        null,
        null,
        null,
        maxRetries,
        callTimeout,
        callTimeout.compareTo(defaultAttempt) < 0 ? callTimeout : defaultAttempt);
  }
}
