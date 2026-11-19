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
import com.azure.identity.CredentialUnavailableException;
import io.camunda.secretstore.SecretStoreMetricsDoc;
import io.camunda.zeebe.test.util.logging.LogCapturer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.Timeout.ThreadMode;

/**
 * Drives the real credential chain against {@link StubAzureHttpClient}, so what is counted and
 * logged is what a failing Entra ID sign-in produces, not a hand-made exception.
 */
final class SignInMonitorTest {

  private static final String PHYSICAL_TENANT = "pt1";
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
    // given sign-in that has been failing since it last succeeded
    final var http = StubAzureHttpClient.signingIn(3600);
    final var credential = servicePrincipalChain(http);
    credential.getTokenSync(new TokenRequestContext().addScopes("https://first/.default"));
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
  void shouldLogTheFirstSignInWhenSignInFailedAtStartup() {
    // given sign-in that has failed since the store started
    final var http = StubAzureHttpClient.signingIn(3600);
    final var credential = servicePrincipalChain(http);
    http.rejectSignIn(true);
    signInFailing(credential);

    try (final var log = LogCapturer.capturing(SignInMonitor.class, Level.INFO)) {
      // when
      http.rejectSignIn(false);
      credential.getTokenSync(VAULT_SCOPE);

      // then it is the first sign-in, not a recovery
      assertThat(log.messagesAt(Level.INFO))
          .singleElement()
          .asString()
          .contains(STORE_ID, "signed in with", AzureCredentialChain.ENVIRONMENT)
          .doesNotContain("again");
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
      assertThat(log.messagesAt(Level.INFO)).hasSize(2);
      assertThat(log.messagesAt(Level.WARN)).hasSize(1);
      assertThat(log.messagesAt(Level.DEBUG)).hasSize(1);
      assertThat(log.containsInMessagesOrThrowables(CLIENT_SECRET)).isFalse();
      assertThat(log.containsInMessagesOrThrowables(StubAzureHttpClient.TOKEN)).isFalse();
      assertThat(meterRegistry.getMeters())
          .flatExtracting(meter -> meter.getId().getTags())
          .noneMatch(tag -> tag.getValue().contains(CLIENT_SECRET));
    }
  }

  @Test
  void shouldNotLogTheTokenOfASignInResponseItCannotParse() {
    // given Entra ID answering with a token response cut off inside the token, which the SDK's
    // JSON parser quotes in its error
    final var credential =
        servicePrincipalChain(
            StubAzureHttpClient.answeringSignIn(
                200,
                "{\"token_type\":\"Bearer\",\"expires_in\":3600,\"access_token\":\""
                    + StubAzureHttpClient.TOKEN));

    try (final var log = LogCapturer.capturing(SignInMonitor.class, Level.DEBUG)) {
      // when
      signInFailing(credential);
      signInFailing(credential);

      // then the reason is logged, but not the response
      assertThat(log.messagesAt(Level.WARN))
          .singleElement()
          .asString()
          .contains("the sign-in response could not be parsed");
      assertThat(log.containsInMessagesOrThrowables(StubAzureHttpClient.TOKEN)).isFalse();
    }
  }

  @Test
  void shouldNotLogTheTokenTheParserQuotesInItsDiagnostic() {
    // given Entra ID answering with an unquoted token, which the SDK's JSON parser names in its
    // diagnostic, ahead of the response it quotes
    final var token = "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9";
    final var credential =
        servicePrincipalChain(
            StubAzureHttpClient.answeringSignIn(
                200,
                "{\"token_type\":\"Bearer\",\"expires_in\":3600,\"access_token\":" + token + "}"));

    try (final var log = LogCapturer.capturing(SignInMonitor.class, Level.DEBUG)) {
      // when
      signInFailing(credential);
      signInFailing(credential);

      // then
      assertThat(log.messagesAt(Level.WARN)).hasSize(1);
      assertThat(log.containsInMessagesOrThrowables(token)).isFalse();
    }
  }

  @Test
  void shouldNotLogAValueQuotedByAParseErrorAnSdkWrapperCopiedIntoItsMessage() {
    // given the SDK's sync client wrapping a failure as it does, copying the message of the
    // ExecutionException, which itself quotes the cause's toString()
    final var value = "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9";
    final var execution =
        new ExecutionException(new NumberFormatException("For input string: \"" + value + "\""));
    final var failure = new ClientAuthenticationException(execution.getMessage(), null, execution);

    // when
    final var reason = SignInMonitor.reason(failure);

    // then
    assertThat(reason).doesNotContain(value).contains("could not be parsed");
  }

  @Test
  @Timeout(value = 5, threadMode = ThreadMode.SEPARATE_THREAD)
  void shouldDescribeAFailureWhoseCausesFormACycle() {
    // given
    final var first = new IllegalStateException("first");
    final var second = new IllegalStateException("second", first);
    first.initCause(second);

    // when
    final var reason = SignInMonitor.reason(first);

    // then
    assertThat(reason).isEqualTo("first Caused by: second");
  }

  @Test
  void shouldWarnWithTheReasonAManagedIdentityFailedToSignIn() {
    // given IMDS refusing an identity the VM is not assigned; the SDK's own message only says to
    // see the inner exception
    final var http =
        StubAzureHttpClient.answeringSignIn(
            400, "{\"error\":\"invalid_request\",\"error_description\":\"Identity not found\"}");
    final var credential =
        AzureCredentialChain.build(
            new AzureKeyVaultStoreConfig(
                VAULT_URL,
                null,
                null,
                "unassigned-identity",
                null,
                AzureKeyVaultStoreConfig.DEFAULT_MAX_RETRIES,
                AzureKeyVaultStoreConfig.DEFAULT_CALL_TIMEOUT,
                AzureKeyVaultStoreConfig.DEFAULT_ATTEMPT_TIMEOUT),
            environment(Map.of()),
            http,
            executor,
            new SignInMonitor(PHYSICAL_TENANT, STORE_ID, VAULT_URL, meterRegistry));

    try (final var log = LogCapturer.capturing(SignInMonitor.class, Level.WARN)) {
      // when
      signInFailing(credential);

      // then
      assertThat(log.messagesAt(Level.WARN))
          .singleElement()
          .asString()
          .contains("ManagedIdentityCredential", "Identity not found");
    }
  }

  @Test
  void shouldLogTheReasonOnASingleLine() {
    // given the chain's failure when no sign-in method is available, which joins the reason of
    // each method with a line break
    final var monitor = new SignInMonitor(PHYSICAL_TENANT, STORE_ID, VAULT_URL, meterRegistry);

    try (final var log = LogCapturer.capturing(SignInMonitor.class, Level.WARN)) {
      // when
      monitor.failed(
          "any sign-in method",
          new CredentialUnavailableException("first unavailable\r\nsecond unavailable"));

      // then
      assertThat(log.messagesAt(Level.WARN))
          .singleElement()
          .asString()
          .contains("first unavailable second unavailable")
          .doesNotContain("\n");
    }
  }

  @Test
  void shouldIgnoreTokenRequestsEndingAfterClose() {
    // given a store shutting down, which interrupts the token requests still running
    final var monitor = new SignInMonitor(PHYSICAL_TENANT, STORE_ID, VAULT_URL, meterRegistry);
    monitor.close();

    try (final var log = LogCapturer.capturing(SignInMonitor.class, Level.DEBUG)) {
      // when
      monitor.failed("ManagedIdentityCredential", new RuntimeException("interrupted"));

      // then
      assertThat(log.messages()).isEmpty();
    }
  }

  @Test
  void shouldRemoveTheMeterOnClose() {
    // given
    final var monitor = new SignInMonitor(PHYSICAL_TENANT, STORE_ID, VAULT_URL, meterRegistry);

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
        new SignInMonitor(PHYSICAL_TENANT, STORE_ID, VAULT_URL, meterRegistry));
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
