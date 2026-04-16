/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler.retry;

/**
 * Exception thrown when the maximum retry limit has been reached. This indicates that a retry
 * strategy has exhausted its allowed retries without the operation succeeding.
 */
public class MaxRetryReachedException extends RuntimeException {

  public MaxRetryReachedException(final String message) {
    super(message);
  }

  public MaxRetryReachedException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
