/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema;

/**
 * Thrown when the cluster health check fails due to insufficient privileges. Unlike transient
 * failures, this is a permanent condition that should not be retried — the configured credentials
 * lack the {@code monitor} cluster privilege required by {@code GET /_cluster/health}.
 */
public class SearchEngineHealthCheckPermissionException extends RuntimeException {

  public SearchEngineHealthCheckPermissionException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
