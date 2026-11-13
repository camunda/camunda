/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.azure;

import io.camunda.secretstore.SecretStoreMetricsDoc;
import io.camunda.secretstore.SecretStoreMetricsDoc.SecretStoreKeyNames;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tells the operator how one {@link AzureKeyVaultSecretStore} signs in to Microsoft Entra ID: in
 * the log, and on {@link SecretStoreMetricsDoc#SIGNIN_FAILURES}.
 *
 * <p>One instance per store, shared by every credential in its chain. The log follows the store's
 * sign-in state rather than each token request, so a credential that keeps failing on every call
 * does not bury its cause under hundreds of identical lines:
 *
 * <ul>
 *   <li>INFO once, naming the credential, when the store first signs in
 *   <li>WARN once, naming the credential and the reason, when sign-in starts failing, at startup or
 *       after a success; each further failure only at DEBUG, with its stack trace
 *   <li>INFO once when sign-in recovers
 * </ul>
 *
 * <p>Every failure is counted, whether or not it is logged. Only a credential's {@link
 * SignInCredential#description() description} and the SDK's error message are ever logged: never a
 * token, a client secret or a certificate.
 *
 * <p>Thread-safe: tokens are fetched on the store's credential executor and on the threads calling
 * the vault.
 */
final class SignInMonitor implements AutoCloseable {

  static final String STORE_TYPE = "azure";

  private static final Logger LOG = LoggerFactory.getLogger(SignInMonitor.class);

  private final String storeId;
  private final String vaultUrl;
  private final MeterRegistry meterRegistry;
  private final Counter failures;
  private final AtomicReference<State> state = new AtomicReference<>(State.STARTING);

  SignInMonitor(final String storeId, final String vaultUrl, final MeterRegistry meterRegistry) {
    this.storeId = storeId;
    this.vaultUrl = vaultUrl;
    this.meterRegistry = meterRegistry;
    final var meter = SecretStoreMetricsDoc.SIGNIN_FAILURES;
    failures =
        Counter.builder(meter.getName())
            .description(meter.getDescription())
            .tag(SecretStoreKeyNames.STORE.asString(), storeId)
            .tag(SecretStoreKeyNames.STORE_TYPE.asString(), STORE_TYPE)
            .register(meterRegistry);
  }

  /** Called with the description of the credential that has just obtained a token. */
  void succeeded(final String credential) {
    switch (state.getAndSet(State.SIGNED_IN)) {
      case STARTING ->
          LOG.info(
              "Azure Key Vault store '{}' for '{}' signed in with {}",
              storeId,
              vaultUrl,
              credential);
      case FAILING ->
          LOG.info(
              "Azure Key Vault store '{}' for '{}' signed in again with {}, after failing to",
              storeId,
              vaultUrl,
              credential);
      case SIGNED_IN -> {}
    }
  }

  /**
   * Called when a token request has failed.
   *
   * @param credential what was trying to sign in: a credential's description, or the whole chain
   *     when no sign-in method was available
   */
  void failed(final String credential, final Throwable failure) {
    failures.increment();
    if (state.getAndSet(State.FAILING) != State.FAILING) {
      LOG.warn(
          "Azure Key Vault store '{}' for '{}' failed to sign in with {}: {}. Further failures are"
              + " logged at DEBUG until sign-in recovers, and counted on {}",
          storeId,
          vaultUrl,
          credential,
          failure.getMessage(),
          SecretStoreMetricsDoc.SIGNIN_FAILURES.getName());
    } else {
      LOG.debug(
          "Azure Key Vault store '{}' for '{}' failed to sign in with {} again",
          storeId,
          vaultUrl,
          credential,
          failure);
    }
  }

  /**
   * Removes the meter, so a store rebuilt with the same ID does not leave a stale series behind.
   */
  @Override
  public void close() {
    meterRegistry.remove(failures);
  }

  private enum State {
    STARTING,
    SIGNED_IN,
    FAILING
  }
}
