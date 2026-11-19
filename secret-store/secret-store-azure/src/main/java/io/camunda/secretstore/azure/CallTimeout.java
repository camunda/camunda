/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.azure;

import io.camunda.secretstore.SecretStoreUnavailableException;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Bounds one synchronous call to the vault, retries included, by the store's call timeout. The
 * SDK's synchronous calls take no overall timeout, so the call runs on the store's executor and the
 * caller stops waiting past the timeout, requesting an interrupt of the call, so that a slow vault
 * cannot stall the IO thread resolution shares with the exporters (camunda/camunda#62869).
 */
final class CallTimeout {

  private final ExecutorService executor;
  private final Duration timeout;

  CallTimeout(final ExecutorService executor, final Duration timeout) {
    this.executor = executor;
    this.timeout = timeout;
  }

  /**
   * Runs {@code call}, rethrowing its own exception, so the caller can classify it.
   *
   * @param what what the call does, for the message, e.g. {@code "read secret api-key"}
   * @throws SecretStoreUnavailableException past the call timeout, if the caller is interrupted
   *     (its interrupt is kept), or if the store is closed
   */
  <T> T call(final String what, final Supplier<T> call) {
    // Future.get returns an already-completed result without checking the interrupt, so an
    // interrupted caller must be refused before the call starts, not only while it waits
    if (Thread.currentThread().isInterrupted()) {
      throw interrupted(what, null);
    }
    final Future<T> future;
    try {
      future = executor.submit(call::get);
    } catch (final RejectedExecutionException e) {
      throw new SecretStoreUnavailableException(
          "Azure Key Vault store is closed: cannot " + what, e);
    }
    try {
      return future.get(TimeUnit.NANOSECONDS.convert(timeout), TimeUnit.NANOSECONDS);
    } catch (final TimeoutException e) {
      future.cancel(true);
      throw new SecretStoreUnavailableException(
          "Azure Key Vault is unavailable: it did not "
              + what
              + " within the call timeout of "
              + timeout,
          e);
    } catch (final InterruptedException e) {
      future.cancel(true);
      Thread.currentThread().interrupt();
      throw interrupted(what, e);
    } catch (final ExecutionException e) {
      final var cause = e.getCause();
      if (cause instanceof final RuntimeException runtime) {
        throw runtime;
      }
      if (cause instanceof final Error error) {
        throw error;
      }
      throw new SecretStoreUnavailableException(
          "Azure Key Vault is unavailable: cannot " + what, cause);
    }
  }

  private static SecretStoreUnavailableException interrupted(
      final String what, final @Nullable InterruptedException cause) {
    return new SecretStoreUnavailableException(
        "Interrupted while waiting for Azure Key Vault to " + what, cause);
  }
}
