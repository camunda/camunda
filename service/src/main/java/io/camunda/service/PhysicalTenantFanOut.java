/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.service.exception.ErrorMapper;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.StringJoiner;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * What every cluster-wide service does with the per-physical-tenant outcomes of one fan-out: name
 * the tenants that failed, and answer with a single status.
 *
 * <p>Shared rather than copied per service because the status rule is what a caller scripts
 * against: where every failing tenant agrees on a status the request answers with it, so a request
 * narrowed to one tenant collapses to exactly what that tenant's own endpoint would have answered.
 * Only genuinely different causes have no honest status other than 500.
 */
@NullMarked
final class PhysicalTenantFanOut {

  private PhysicalTenantFanOut() {}

  /**
   * Enforces the all-or-nothing rule: returns every tenant's value in the outcomes' order, or fails
   * the whole request if any tenant failed.
   */
  static <T> List<T> requireEveryTenant(final List<Outcome<T>> outcomes) {
    requireNoTenantFailed(outcomes);
    return outcomes.stream().map(Outcome::requireValue).toList();
  }

  /**
   * The same rule for a call that produces no value, whose successful outcomes therefore carry no
   * value to return — a {@code CompletionStage<Void>} completes with {@code null}.
   */
  static void requireNoTenantFailed(final List<? extends Outcome<?>> outcomes) {
    final var failures = outcomes.stream().filter(Outcome::isFailure).toList();
    if (!failures.isEmpty()) {
      throw combine(failures, outcomes.size());
    }
  }

  /** Folds the failures of a fan-out into the one exception the request answers with. */
  static ServiceException combine(final List<? extends Outcome<?>> failures, final int targeted) {
    final var statuses = new LinkedHashSet<Status>();
    final var detail = new StringJoiner("; ");
    for (final var failure : failures) {
      final var cause = failure.cause();
      statuses.add(cause.getStatus());
      detail.add("'%s': %s".formatted(failure.physicalTenantId(), cause.getMessage()));
    }
    return new ServiceException(
        "Expected to serve every targeted physical tenant, but %d of %d failed — %s"
            .formatted(failures.size(), targeted, detail),
        sharedStatus(statuses));
  }

  /**
   * The one status a set of failures agrees on, or {@link Status#INTERNAL} when they disagree and
   * no single status is honest.
   */
  static Status sharedStatus(final Collection<Status> statuses) {
    final var distinct = new LinkedHashSet<>(statuses);
    return distinct.size() == 1 ? distinct.iterator().next() : Status.INTERNAL;
  }

  /**
   * One physical tenant's outcome, successful or not. Carries the tenant id so a failure on a
   * ten-tenant cluster tells the operator which tenant failed, not just that one did — the exact
   * toil the cluster-wide endpoints exist to remove.
   */
  record Outcome<T>(String physicalTenantId, @Nullable T value, @Nullable Throwable error) {

    static <T> Outcome<T> failed(final String physicalTenantId, final Throwable error) {
      return new Outcome<>(physicalTenantId, null, error);
    }

    boolean isFailure() {
      return error != null;
    }

    T requireValue() {
      if (value == null) {
        throw new IllegalStateException(
            "Expected physical tenant '%s' to have succeeded".formatted(physicalTenantId));
      }
      return value;
    }

    /**
     * The failure mapped to a {@link ServiceException}, so both its status and its message are the
     * ones the tenant's own endpoint would have answered with.
     *
     * <p>Maps through {@link ErrorMapper} rather than matching on the raw throwable because a
     * {@link java.util.concurrent.CompletableFuture} wraps a dependency's failure in a {@link
     * java.util.concurrent.CompletionException} — only the recursive unwrap reaches the real cause.
     */
    ServiceException cause() {
      if (error == null) {
        throw new IllegalStateException(
            "Expected physical tenant '%s' to have failed".formatted(physicalTenantId));
      }
      return ErrorMapper.mapError(error);
    }
  }
}
