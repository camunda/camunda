/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.azure;

import com.azure.core.exception.ClientAuthenticationException;
import com.azure.core.exception.HttpResponseException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Checks once, when a store is built, that it can reach its vault, sign in and read what its mode
 * needs, so a wrong vault URL, network rule or role shows in the log before the first secret is
 * resolved.
 *
 * <p>Best effort: the outcome is only logged, and the store is built either way, so a vault that is
 * down cannot stop the cluster from starting. The check never takes longer than the store's call
 * timeout: past it, the call is interrupted and left to the SDK's own timeouts. Should the check
 * itself fail unexpectedly, that is logged too, and the store is still built.
 *
 * <p>Logs one INFO line on success, or one WARN line telling the cause apart, with the stack trace
 * at DEBUG. A failed sign-in is the exception: {@link SignInMonitor} has already logged its WARN,
 * naming the credential and the reason, so it is not repeated here, and neither is the failure,
 * whose cause can quote the sign-in response. That includes a check that runs out of time while
 * sign-in keeps failing, as the SDK retries a failed sign-in with backoff. A check that runs out of
 * time before the store has signed in at all cannot tell the vault from the sign-in endpoint, so
 * its warning names both.
 */
final class StartupCheck {

  private static final Logger LOG = LoggerFactory.getLogger(StartupCheck.class);

  private StartupCheck() {}

  /**
   * @param executor runs the probe, so the caller can stop waiting for it
   * @param signIn the state of the store's sign-in, see {@link SignInMonitor}
   */
  static void run(
      final AzureSecretResolver resolver,
      final ExecutorService executor,
      final Duration timeout,
      final SignInState signIn,
      final String physicalTenantId,
      final String storeId,
      final String vaultUrl) {
    try {
      check(resolver, executor, timeout, signIn, physicalTenantId, storeId, vaultUrl);
    } catch (final RuntimeException e) {
      LOG.warn(
          "Azure Key Vault store '{}' of physical tenant '{}' could not check its access to '{}'."
              + " The cluster starts anyway",
          storeId,
          physicalTenantId,
          vaultUrl,
          e);
    }
  }

  private static void check(
      final AzureSecretResolver resolver,
      final ExecutorService executor,
      final Duration timeout,
      final SignInState signIn,
      final String physicalTenantId,
      final String storeId,
      final String vaultUrl) {
    final Future<?> probe = executor.submit(resolver::validateConnectivity);
    try {
      probe.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
      LOG.info(
          "Azure Key Vault store '{}' of physical tenant '{}' can {} at '{}'",
          storeId,
          physicalTenantId,
          resolver.probeDescription(),
          vaultUrl);
    } catch (final InterruptedException e) {
      probe.cancel(true);
      Thread.currentThread().interrupt();
      LOG.info(
          "Startup check of Azure Key Vault store '{}' of physical tenant '{}' was interrupted"
              + " before it could {} at '{}'",
          storeId,
          physicalTenantId,
          resolver.probeDescription(),
          vaultUrl);
    } catch (final TimeoutException e) {
      probe.cancel(true);
      if (signIn.isFailing()) {
        LOG.debug(
            "Azure Key Vault store '{}' of physical tenant '{}' cannot {} at '{}' within {}, as it"
                + " cannot sign in",
            storeId,
            physicalTenantId,
            resolver.probeDescription(),
            vaultUrl,
            timeout);
        return;
      }
      final var cause =
          signIn.isPending()
              ? "it got no answer within the call timeout of "
                  + timeout
                  + ", before signing in: either the vault is unreachable, or signing in to"
                  + " Microsoft Entra ID did not complete"
              : "the vault is unreachable: it did not answer within the call timeout of " + timeout;
      warn(resolver, physicalTenantId, storeId, vaultUrl, new Diagnosis(cause, true), e);
    } catch (final ExecutionException e) {
      final var failure = e.getCause() != null ? e.getCause() : e;
      final var diagnosis = describe(failure, resolver, signIn.isFailing());
      if (diagnosis == null) {
        LOG.debug(
            "Azure Key Vault store '{}' of physical tenant '{}' cannot {} at '{}' as it cannot sign"
                + " in",
            storeId,
            physicalTenantId,
            resolver.probeDescription(),
            vaultUrl);
      } else {
        warn(resolver, physicalTenantId, storeId, vaultUrl, diagnosis, failure);
      }
    }
  }

  private static void warn(
      final AzureSecretResolver resolver,
      final String physicalTenantId,
      final String storeId,
      final String vaultUrl,
      final Diagnosis diagnosis,
      final Throwable failure) {
    LOG.warn(
        "Azure Key Vault store '{}' of physical tenant '{}' cannot {} at '{}': {}. The cluster"
            + " starts anyway{}",
        storeId,
        physicalTenantId,
        resolver.probeDescription(),
        vaultUrl,
        diagnosis.cause(),
        diagnosis.resolutionFails()
            ? ", and secrets of this store fail to resolve until this is fixed"
            : "");
    LOG.debug("Startup check of Azure Key Vault store '{}' failed", storeId, failure);
  }

  /**
   * The cause, as the operator should read it, or {@code null} for a failed sign-in, which {@link
   * SignInMonitor} reports.
   *
   * @param signInFailing whether the store's sign-in is failing; the vault's own 401 reaches the
   *     probe as the same exception type as a failed sign-in, but with the vault's response, while
   *     sign-in keeps working
   */
  static @Nullable Diagnosis describe(
      final Throwable failure, final AzureSecretResolver resolver, final boolean signInFailing) {
    final Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    for (Throwable t = failure; t != null && seen.add(t); t = t.getCause()) {
      if (t instanceof final ClientAuthenticationException authentication
          && (signInFailing || authentication.getResponse() == null)) {
        return null;
      }
      if (t instanceof final HttpResponseException http && http.getResponse() != null) {
        return switch (http.getResponse().getStatusCode()) {
          case 401 ->
              new Diagnosis(
                  "the vault rejected the identity's token, for example because the vault belongs"
                      + " to a different Microsoft Entra tenant",
                  true);
          case 403 ->
              AzureKeyVaultErrors.isSecretDisabled(http)
                  ? new Diagnosis("the secret is disabled in Azure Key Vault", true)
                  : new Diagnosis(
                      "access denied: " + resolver.accessDeniedHint(),
                      resolver.accessDeniedFailsResolution());
          case 404 -> new Diagnosis("not found: " + firstLine(http.getMessage()), true);
          default ->
              new Diagnosis(
                  "the vault answered HTTP "
                      + http.getResponse().getStatusCode()
                      + ": "
                      + firstLine(http.getMessage()),
                  true);
        };
      }
      if (t instanceof UnknownHostException) {
        return new Diagnosis(
            "the vault is unreachable: its host does not resolve (" + t.getMessage() + ")", true);
      }
      if (t instanceof ConnectException) {
        return new Diagnosis(
            "the vault is unreachable: the connection failed (" + t.getMessage() + ")", true);
      }
      if (t instanceof IOException || t instanceof TimeoutException) {
        return new Diagnosis("the vault is unreachable: " + t, true);
      }
    }
    return new Diagnosis(failure.toString(), true);
  }

  // the SDK appends the whole JSON error body on further lines
  private static String firstLine(final @Nullable String message) {
    if (message == null) {
      return "";
    }
    final int end = message.indexOf('\n');
    return end < 0 ? message : message.substring(0, end);
  }

  /**
   * @param cause why the check failed, as the operator should read it
   * @param resolutionFails whether the store's secrets fail to resolve for the same cause
   */
  record Diagnosis(String cause, boolean resolutionFails) {}

  /** What the check needs to know of the store's sign-in, see {@link SignInMonitor}. */
  interface SignInState {

    /** Whether the last token request failed, and none has succeeded since. */
    boolean isFailing();

    /** Whether no token request has completed yet, successfully or not. */
    boolean isPending();
  }
}
