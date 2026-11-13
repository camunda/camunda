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

import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.azure.core.exception.ClientAuthenticationException;
import io.camunda.secretstore.SecretStoreMetricsDoc;
import io.camunda.zeebe.test.util.logging.LogCapturer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Drives the real credential chain against {@link StubAzureHttpClient}, so what is counted and
 * logged is what a failing Entra ID sign-in produces, not a hand-made exception.
 */
final class SignInMonitorTest {

  private static final String STORE_ID = "kv";
  private static final String VAULT_URL = "https://my-vault.vault.azure.net";
  private static final String CLIENT_SECRET = "sp-client-secret-value";
  private static final TokenRequestContext VAULT_SCOPE =
      new TokenRequestContext().addScopes("https://vault.azure.net/.default");

  private final ExecutorService executor = AzureKeyVaultSecretStore.newCredentialExecutor();
  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

  @AfterEach
  void shutDownExecutor() {
    executor.shutdownNow();
  }

  @Test
  void shouldCountEveryFailedSignInForTheStoreAndStoreType() {
    // given
    final var credential = servicePrincipalChain(StubAzureHttpClient.rejecting());

    // when
    signInFailing(credential);
    signInFailing(credential);

    // then
    assertThat(failures()).isEqualTo(2);
  }

  @Test
  void shouldWarnOnceNamingTheCredentialAndReasonWhileSignInKeepsFailing() {
    // given
    final var credential = servicePrincipalChain(StubAzureHttpClient.rejecting());

    try (final var log = LogCapturer.capturing(SignInMonitor.class, Level.DEBUG)) {
      // when
      signInFailing(credential);
      signInFailing(credential);
      signInFailing(credential);

      // then
      assertThat(log.messagesAt(Level.WARN))
          .singleElement()
          .asString()
          .contains(STORE_ID, VAULT_URL, AzureCredentialChain.ENVIRONMENT, "AADSTS7000215");
      assertThat(log.messagesAt(Level.DEBUG)).hasSize(2);
    }
  }

  @Test
  void shouldLogOnceWhenSignInRecovers() {
    // given sign-in that has been failing
    final var http = StubAzureHttpClient.signingIn(3600);
    final var credential = servicePrincipalChain(http);
    http.rejectSignIn(true);
    signInFailing(credential);

    try (final var log = LogCapturer.capturing(SignInMonitor.class, Level.INFO)) {
      // when
      http.rejectSignIn(false);
      credential.getTokenSync(VAULT_SCOPE);
      credential.getTokenSync(VAULT_SCOPE);

      // then
      assertThat(log.messagesAt(Level.INFO))
          .singleElement()
          .asString()
          .contains(STORE_ID, "signed in again", AzureCredentialChain.ENVIRONMENT);
      assertThat(log.messagesAt(Level.WARN)).isEmpty();
    }
  }

  @Test
  void shouldWarnAgainWhenSignInFailsAfterRecovering() {
    // given
    final var http = StubAzureHttpClient.signingIn(3600);
    final var credential = servicePrincipalChain(http);
    http.rejectSignIn(true);
    signInFailing(credential);
    http.rejectSignIn(false);
    credential.getTokenSync(new TokenRequestContext().addScopes("https://first/.default"));

    try (final var log = LogCapturer.capturing(SignInMonitor.class, Level.WARN)) {
      // when — a scope with no cached token, so the credential asks Entra ID again
      http.rejectSignIn(true);
      signInFailing(credential);

      // then
      assertThat(log.messagesAt(Level.WARN)).hasSize(1);
    }
  }

  @Test
  void shouldNeverLogOrTagTheSecretOrToken() {
    // given
    final var http = StubAzureHttpClient.signingIn(3600);
    final var credential = servicePrincipalChain(http);

    try (final var log = LogCapturer.capturing(SignInMonitor.class, Level.DEBUG)) {
      // when — sign in, fail twice, recover
      credential.getTokenSync(VAULT_SCOPE);
      http.rejectSignIn(true);
      signInFailing(credential, "https://second/.default");
      signInFailing(credential, "https://third/.default");
      http.rejectSignIn(false);
      credential.getTokenSync(new TokenRequestContext().addScopes("https://fourth/.default"));

      // then
      assertThat(log.messages()).hasSizeGreaterThanOrEqualTo(4);
      assertThat(log.containsInMessagesOrThrowables(CLIENT_SECRET)).isFalse();
      assertThat(log.containsInMessagesOrThrowables(StubAzureHttpClient.TOKEN)).isFalse();
      assertThat(meterRegistry.getMeters())
          .flatExtracting(meter -> meter.getId().getTags())
          .noneMatch(tag -> tag.getValue().contains(CLIENT_SECRET));
    }
  }

  @Test
  void shouldRemoveTheMeterOnClose() {
    // given
    final var monitor = new SignInMonitor(STORE_ID, VAULT_URL, meterRegistry);

    // when
    monitor.close();

    // then
    assertThat(meterRegistry.find(SecretStoreMetricsDoc.SIGNIN_FAILURES.getName()).meters())
        .isEmpty();
  }

  private TokenCredential servicePrincipalChain(final StubAzureHttpClient http) {
    return AzureCredentialChain.build(
        AzureKeyVaultStoreConfig.of(VAULT_URL),
        environment(
            Map.of(
                "AZURE_TENANT_ID", "tenant-id",
                "AZURE_CLIENT_ID", "sp-client-id",
                "AZURE_CLIENT_SECRET", CLIENT_SECRET)),
        http,
        executor,
        new SignInMonitor(STORE_ID, VAULT_URL, meterRegistry));
  }

  private static void signInFailing(final TokenCredential credential) {
    signInFailing(credential, "https://vault.azure.net/.default");
  }

  private static void signInFailing(final TokenCredential credential, final String scope) {
    assertThatThrownBy(() -> credential.getTokenSync(new TokenRequestContext().addScopes(scope)))
        .isInstanceOf(ClientAuthenticationException.class);
  }

  private double failures() {
    return meterRegistry
        .get(SecretStoreMetricsDoc.SIGNIN_FAILURES.getName())
        .tag("store", STORE_ID)
        .tag("storeType", "azure")
        .counter()
        .count();
  }
}
