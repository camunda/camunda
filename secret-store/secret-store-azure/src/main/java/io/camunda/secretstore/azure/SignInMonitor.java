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
import java.text.ParseException;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
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
 *       after a success; each further failure only at DEBUG
 *   <li>INFO once when sign-in recovers
 * </ul>
 *
 * <p>Every failure is counted, whether or not it is logged. Once closed, the monitor ignores token
 * requests that the store's shutdown interrupts. Only a credential's {@link
 * SignInCredential#description() description} and the {@link #reason(Throwable) reason} it failed
 * are logged: never a token, a client secret or a certificate.
 *
 * <p>Thread-safe: tokens are fetched on the store's credential executor and on the threads calling
 * the vault.
 */
final class SignInMonitor implements AutoCloseable {

  static final String STORE_TYPE = "azure";

  private static final Logger LOG = LoggerFactory.getLogger(SignInMonitor.class);

  // the SDK's JSON parser ends its diagnostics with this marker; the diagnostic quotes the token it
  // stumbled on and the marker up to 500 characters of the response, which can hold a token
  private static final String PARSER_DIAGNOSTIC = "[Source:";
  private static final String UNPARSABLE_RESPONSE =
      "the sign-in response could not be parsed [response omitted]";
  private static final Pattern LINE_BREAKS = Pattern.compile("\\s*\\R\\s*");

  private final String physicalTenantId;
  private final String storeId;
  private final String vaultUrl;
  private final MeterRegistry meterRegistry;
  private final Counter failures;
  private final AtomicReference<State> state = new AtomicReference<>(State.STARTING);

  SignInMonitor(
      final String physicalTenantId,
      final String storeId,
      final String vaultUrl,
      final MeterRegistry meterRegistry) {
    this.physicalTenantId = physicalTenantId;
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
    switch (state.getAndUpdate(current -> current == State.CLOSED ? current : State.SIGNED_IN)) {
      case STARTING, FAILING_TO_START ->
          LOG.info(
              "Azure Key Vault store '{}' of physical tenant '{}' for '{}' signed in with {}",
              storeId,
              physicalTenantId,
              vaultUrl,
              credential);
      case FAILING ->
          LOG.info(
              "Azure Key Vault store '{}' of physical tenant '{}' for '{}' signed in again with {},"
                  + " after failing to",
              storeId,
              physicalTenantId,
              vaultUrl,
              credential);
      default -> {
        // already signed in, where logging every token would flood the log, or closed
      }
    }
  }

  /**
   * Called when a token request has failed.
   *
   * @param credential what was trying to sign in: a credential's description, or the whole chain
   *     when no sign-in method was available
   */
  void failed(final String credential, final Throwable failure) {
    final var previous = state.getAndUpdate(SignInMonitor::failing);
    if (previous == State.CLOSED) {
      return;
    }
    failures.increment();
    if (previous == State.STARTING || previous == State.SIGNED_IN) {
      LOG.warn(
          "Azure Key Vault store '{}' of physical tenant '{}' for '{}' failed to sign in with {}:"
              + " {}. Further failures are logged at DEBUG until sign-in recovers, and counted"
              + " on {}",
          storeId,
          physicalTenantId,
          vaultUrl,
          credential,
          reason(failure),
          SecretStoreMetricsDoc.SIGNIN_FAILURES.getName());
    } else if (LOG.isDebugEnabled()) {
      LOG.debug(
          "Azure Key Vault store '{}' of physical tenant '{}' for '{}' failed to sign in with {}"
              + " again: {}",
          storeId,
          physicalTenantId,
          vaultUrl,
          credential,
          reason(failure));
    }
  }

  /**
   * The failure's message, followed by its root cause's when that adds something: a managed
   * identity, for one, says only "see inner exception". The stack trace is left out. A failure
   * caused anywhere in its chain by parsing the response is reported with a fixed reason instead,
   * because parse errors quote the input they could not parse, and SDK wrappers copy those messages
   * into their own.
   */
  static String reason(final Throwable failure) {
    final Set<Throwable> chain = Collections.newSetFromMap(new IdentityHashMap<>());
    var root = failure;
    for (var current = failure;
        current != null && chain.add(current);
        current = current.getCause()) {
      if (isParseFailure(current)) {
        return UNPARSABLE_RESPONSE;
      }
      root = current;
    }
    final var message = messageOf(failure);
    final var rootMessage = messageOf(root);
    return root == failure || message.contains(rootMessage)
        ? message
        : message + " Caused by: " + rootMessage;
  }

  private static boolean isParseFailure(final Throwable failure) {
    return failure instanceof NumberFormatException
        || failure instanceof DateTimeParseException
        || failure instanceof ParseException
        || (failure.getMessage() != null && failure.getMessage().contains(PARSER_DIAGNOSTIC));
  }

  private static String messageOf(final Throwable failure) {
    final var message = failure.getMessage();
    if (message == null) {
      return failure.getClass().getSimpleName();
    }
    // the chain joins the messages of its unavailable credentials with line breaks
    return LINE_BREAKS.matcher(message.strip()).replaceAll(" ");
  }

  private static State failing(final State current) {
    return switch (current) {
      case STARTING, FAILING_TO_START -> State.FAILING_TO_START;
      case SIGNED_IN, FAILING -> State.FAILING;
      case CLOSED -> State.CLOSED;
    };
  }

  /**
   * Removes the meter, so a store rebuilt with the same ID does not leave a stale series behind,
   * and stops reporting.
   */
  @Override
  public void close() {
    state.set(State.CLOSED);
    meterRegistry.remove(failures);
  }

  private enum State {
    STARTING,
    FAILING_TO_START,
    SIGNED_IN,
    FAILING,
    CLOSED
  }
}
