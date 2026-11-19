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

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.CredentialUnavailableException;
import io.camunda.zeebe.test.util.logging.LogCapturer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.OffsetDateTime;
import org.apache.logging.log4j.Level;
import org.junit.jupiter.api.Test;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;

final class SignInCredentialTest {

  private static final String VAULT_URL = "https://my-vault.vault.azure.net";
  private static final String TOKEN = "access-token";
  private static final TokenRequestContext VAULT_SCOPE =
      new TokenRequestContext().addScopes("https://vault.azure.net/.default");

  @Test
  void shouldLogOnceWhichCredentialSignedInWithoutTheToken() {
    // given
    final var credential =
        new SignInCredential(
            "ManagedIdentityCredential",
            "ManagedIdentityCredential (system-assigned identity)",
            request -> Mono.just(new AccessToken(TOKEN, OffsetDateTime.now().plusHours(1))),
            new SignInMonitor("pt1", "default", VAULT_URL, new SimpleMeterRegistry()));

    try (final var log = LogCapturer.capturing(SignInMonitor.class, Level.INFO)) {
      // when — tokens fetched both ways and again, as the vault client refreshes them
      credential.getTokenSync(VAULT_SCOPE);
      credential.getToken(VAULT_SCOPE).block();
      credential.getTokenSync(VAULT_SCOPE);

      // then
      assertThat(log.messages())
          .singleElement()
          .asString()
          .contains(VAULT_URL)
          .contains("ManagedIdentityCredential (system-assigned identity)")
          .doesNotContain(TOKEN);
    }
  }

  @Test
  void shouldPassAnUnavailableCredentialThroughWithoutReportingIt() {
    // given — not configured here, so the chain moves on to the next method: not a failure
    final var unavailable = new CredentialUnavailableException("not configured");
    final var credential =
        new SignInCredential(
            "EnvironmentCredential",
            "EnvironmentCredential (service principal)",
            request -> Mono.error(unavailable),
            new SignInMonitor("pt1", "default", VAULT_URL, new SimpleMeterRegistry()));

    try (final var log = LogCapturer.capturing(SignInMonitor.class, Level.INFO)) {
      // when / then
      assertThatThrownBy(() -> credential.getTokenSync(VAULT_SCOPE)).isSameAs(unavailable);
      assertThat(log.messages()).isEmpty();
    }
  }

  @Test
  void shouldNotReportATokenRequestInterruptedByTheCallTimeout() {
    // given — the store interrupts a call past its timeout, which may be fetching a token
    final var interrupted = Exceptions.propagate(new InterruptedException());
    final var credential =
        new SignInCredential(
            "EnvironmentCredential",
            "EnvironmentCredential (service principal)",
            request -> Mono.error(interrupted),
            new SignInMonitor("pt1", "default", VAULT_URL, new SimpleMeterRegistry()));

    try (final var log = LogCapturer.capturing(SignInMonitor.class, Level.INFO)) {
      // when / then
      assertThatThrownBy(() -> credential.getTokenSync(VAULT_SCOPE)).isSameAs(interrupted);
      assertThat(log.messages()).isEmpty();
    }
  }
}
