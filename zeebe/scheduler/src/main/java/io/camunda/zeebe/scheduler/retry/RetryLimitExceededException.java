/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler.retry;

public final class RetryLimitExceededException extends RuntimeException {

  private final int retryLimit;

  public RetryLimitExceededException(final int retryLimit, final Throwable cause) {
    super("Retry limit of " + retryLimit + " exceeded", cause);
    this.retryLimit = retryLimit;
  }

  public int getRetryLimit() {
    return retryLimit;
  }
}
