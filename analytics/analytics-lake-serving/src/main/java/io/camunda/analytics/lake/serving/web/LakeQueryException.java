/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.web;

/**
 * Wraps a query-execution {@link java.sql.SQLException} as a client-facing error — a malformed or
 * failing free-form SQL statement is the caller's fault, not a server fault.
 */
public class LakeQueryException extends RuntimeException {

  public LakeQueryException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
