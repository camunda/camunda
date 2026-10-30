/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.expression;

import io.camunda.zeebe.el.EvaluationContext;
import io.camunda.zeebe.util.Either;

/**
 * An {@link EvaluationContext} that can be specialized (scoped) for specific resolution domains,
 *
 * <p>Scoping produces a view that affects subsequent {@link #getVariable(String)} calls. The
 * default methods are no-ops (return {@code this}); implementations override them to return a
 * scoped view when applicable.
 *
 * <h3>None instance</h3>
 *
 * {@link #NONE_INSTANCE} represents an empty context: any lookup returns {@code Left(null)} to
 * denote absence.
 *
 * <h3>Examples</h3>
 *
 * <pre>{@code
 * ScopedEvaluationContext base = ...; // implementation-defined
 *
 * // Process scope:
 * ScopedEvaluationContext processCtx = base.processScoped(2251799813685248L);
 *
 * // Tenant scope:
 * ScopedEvaluationContext tenantCtx = processCtx.tenantScoped("acme-tenant");
 *
 * // Use like a regular EvaluationContext:
 * Either<DirectBuffer, EvaluationContext> v = tenantCtx.getVariable("orderId");
 * }</pre>
 */
@FunctionalInterface
public interface ScopedEvaluationContext extends EvaluationContext {

  /** A context that contains no variables. All lookups yield {@code Left(null)}. */
  ScopedEvaluationContext NONE_INSTANCE = unused -> Either.left(null);

  /**
   * Returns a view of this context scoped to the given process scope key.
   *
   * <p>Implementations may use {@code scopeKey} to select the appropriate source of variables
   * (e.g., process instance variables), or return {@code this} if process scoping is not
   * applicable.
   *
   * @param scopeKey the process scope key (e.g., process instance key)
   * @return a context view reflecting the requested process scope; by default returns {@code this}
   */
  default ScopedEvaluationContext processScoped(final long scopeKey) {
    return this;
  }

  /**
   * Returns a view of this context scoped to the given tenant.
   *
   * <p>Implementations may use {@code tenantId} to select tenant-specific variable sources, or
   * return {@code this} if tenant scoping is not applicable.
   *
   * @param tenantId the tenant identifier
   * @return a context view reflecting the requested tenant scope; by default returns {@code this}
   */
  default ScopedEvaluationContext tenantScoped(final String tenantId) {
    return this;
  }
}
