/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.exceptions;

/**
 * Thrown when a delete-by-query or update-by-query task reports partial per-document failures (e.g.
 * a version conflict caused by a concurrent write), even though the task itself completed.
 */
public class OptimizeByQueryFailureException extends OptimizeRuntimeException {

  public OptimizeByQueryFailureException(final String detailedErrorMessage) {
    super(detailedErrorMessage);
  }

  public OptimizeByQueryFailureException(final String detailedErrorMessage, final Throwable e) {
    super(detailedErrorMessage, e);
  }
}
