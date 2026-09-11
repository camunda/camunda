/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.util;

/**
 * Raised when the secondary storage rejected a bulk request because of its size, rather than
 * because of anything about the documents in it. Tasks can catch this to build a smaller request on
 * the next attempt; every other failure keeps its usual exception type, since writing less would
 * not help.
 */
public final class BulkRequestTooLargeException extends RuntimeException {

  public BulkRequestTooLargeException(final String message) {
    super(message);
  }

  public BulkRequestTooLargeException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
