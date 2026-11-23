/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.startup;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jspecify.annotations.NullMarked;

/**
 * Blocks on a future while a bean is being created, failing with a message that names the step that
 * did not complete: interrupted, timed out, or failed with the future's cause.
 *
 * <p>Use this instead of {@code join()}: when the application is closed while still starting (e.g.
 * SIGTERM), Spring may interrupt the startup thread. {@link
 * java.util.concurrent.CompletableFuture#join()} ignores that interrupt and keeps blocking, and
 * {@code ActorFuture#join()} rethrows it as a bare exception without a message.
 */
@NullMarked
public final class StartupFutures {

  private StartupFutures() {}

  /**
   * Waits for the given startup step without a timeout. Only for steps that depend on something
   * external and can legitimately take long, e.g. acquiring a lease or copying data; any step that
   * should be quick must use {@link #await(String, Future, Duration)} so that a stuck step fails
   * startup instead of blocking it.
   *
   * @param step what is being waited for, e.g. {@code "the gateway to start"}
   */
  public static <T> T await(final String step, final Future<T> future) {
    try {
      return future.get();
    } catch (final InterruptedException e) {
      throw interrupted(step, e);
    } catch (final ExecutionException e) {
      throw failed(step, e);
    }
  }

  /**
   * Waits for the given startup step for at most {@code timeout}.
   *
   * @param step what is being waited for, e.g. {@code "the gateway to start"}
   */
  public static <T> T await(final String step, final Future<T> future, final Duration timeout) {
    try {
      return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (final InterruptedException e) {
      throw interrupted(step, e);
    } catch (final ExecutionException e) {
      throw failed(step, e);
    } catch (final TimeoutException e) {
      throw new IllegalStateException(
          "Timed out after %s waiting for %s".formatted(timeout, step), e);
    }
  }

  private static IllegalStateException interrupted(
      final String step, final InterruptedException e) {
    Thread.currentThread().interrupt();
    return new IllegalStateException(
        "Interrupted while waiting for %s, likely because the application is shutting down"
            .formatted(step),
        e);
  }

  private static IllegalStateException failed(final String step, final ExecutionException e) {
    final var cause = e.getCause() != null ? e.getCause() : e;
    return new IllegalStateException(
        "Failed while waiting for %s: %s".formatted(step, cause.getMessage()), cause);
  }
}
