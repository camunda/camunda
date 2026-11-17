/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.exceptions;

/**
 * Thrown when a bulk request reports per-item failures even though the request itself completed —
 * the bulk counterpart of {@link OptimizeByQueryFailureException}.
 *
 * <p>Exists so that callers which retry can tell this apart from a plain {@link
 * OptimizeRuntimeException}. The common cause is transient back-pressure: a loaded cluster rejects
 * individual operations with {@code es_rejected_execution_exception} (HTTP 429) while the bulk call
 * itself returns successfully, so no transport-level exception is ever thrown and a retry a moment
 * later usually succeeds.
 *
 * <p>The database clients raise a bare {@code OptimizeRuntimeException} for this case, so a caller
 * that wants it retried translates it at its own boundary rather than the clients changing the type
 * for every bulk caller at once.
 */
public class OptimizeBulkFailureException extends OptimizeRuntimeException {

  public OptimizeBulkFailureException(final String detailedErrorMessage) {
    super(detailedErrorMessage);
  }

  public OptimizeBulkFailureException(final String detailedErrorMessage, final Throwable e) {
    super(detailedErrorMessage, e);
  }
}
