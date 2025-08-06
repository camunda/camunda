/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation.itemprovider.retry;

import java.util.concurrent.Callable;

/**
 * An interface for executing retryable queries with a retry mechanism. Implementations should
 * handle the logic for retrying operations that may fail.
 */
public interface RetryingQueryExecutor {

  /**
   * Executes a retryable operation, retrying it if it fails.
   *
   * @param retryableOperation the operation to execute, which may throw an exception
   * @param <V> the type of the result returned by the operation
   * @return the result of the operation if successful
   * @throws RuntimeException if the operation fails after all retries
   */
  <V> V runRetryable(Callable<V> retryableOperation);
}
