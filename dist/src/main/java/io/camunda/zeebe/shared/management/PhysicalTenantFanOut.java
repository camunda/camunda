/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Runs one request per physical tenant for an actuator operation that covers several of them (ADR
 * 003 D3).
 *
 * <p>Every request is started before any is waited on, so a cluster-wide operation costs the same
 * round trips as a single-tenant one. Each tenant's outcome is kept rather than collapsed, because
 * a cluster-wide operation is a set of independent per-tenant operations: it can half-succeed, and
 * the response has to be able to say which half.
 */
@NullMarked
final class PhysicalTenantFanOut {

  private PhysicalTenantFanOut() {}

  /**
   * Calls {@code call} for every tenant in {@code targets}, in the order given, and returns each
   * tenant's outcome in that same order.
   *
   * <p>A call that throws synchronously — the broker-client services validate topology before
   * building a future, so this is the normal shape of "this tenant has no leader" — is turned into
   * a failed outcome rather than propagated. Otherwise the first such tenant would abort the loop
   * and the remaining tenants would never be attempted, which for a pause or resume would leave the
   * cluster in a mixed state that no part of the response mentions.
   *
   * <p>Both failure shapes therefore surface as a {@link CompletionException}, which callers' error
   * mapping treats identically.
   */
  static <T> SequencedMap<String, Outcome<T>> over(
      final List<String> targets, final Function<String, CompletionStage<T>> call) {
    final var started = new LinkedHashMap<String, CompletableFuture<T>>();
    for (final var tenant : targets) {
      try {
        started.put(tenant, call.apply(tenant).toCompletableFuture());
      } catch (final Exception e) {
        started.put(tenant, CompletableFuture.failedFuture(e));
      }
    }

    final var outcomes = new LinkedHashMap<String, Outcome<T>>();
    started.forEach(
        (tenant, future) -> {
          try {
            outcomes.put(tenant, new Outcome<>(future.join(), null));
          } catch (final Exception e) {
            outcomes.put(tenant, new Outcome<>(null, e));
          }
        });
    return outcomes;
  }

  /** The first tenant's failure in fan-out order, or {@code null} if every tenant succeeded. */
  static @Nullable Throwable firstFailure(final Map<String, ? extends Outcome<?>> outcomes) {
    for (final var outcome : outcomes.values()) {
      if (outcome.error() != null) {
        return outcome.error();
      }
    }
    return null;
  }

  /** One tenant's result: exactly one of {@code value} and {@code error} is set. */
  record Outcome<T>(@Nullable T value, @Nullable Throwable error) {}
}
