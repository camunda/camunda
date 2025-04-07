/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.opensearch.client.sync;

import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.store.opensearch.client.OpenSearchFailedShardsException;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import net.jodah.failsafe.function.CheckedSupplier;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.tasks.GetTasksResponse;
import org.opensearch.client.opensearch.tasks.Info;
import org.slf4j.Logger;

public abstract class OpenSearchRetryOperation extends OpenSearchSyncOperation {
  public static final int UPDATE_RETRY_COUNT = 3;
  public static final int DEFAULT_DELAY_INTERVAL_IN_SECONDS = 2;

  public static final int DEFAULT_NUMBER_OF_RETRIES =
      30 * 10; // 30*10 with 2 seconds = 10 minutes retry loop
  private final int delayIntervalInSeconds = DEFAULT_DELAY_INTERVAL_IN_SECONDS;

  private final int numberOfRetries = DEFAULT_NUMBER_OF_RETRIES;

  public OpenSearchRetryOperation(final Logger logger, final OpenSearchClient openSearchClient) {
    super(logger, openSearchClient);
  }

  protected <T> T executeWithRetries(final CheckedSupplier<T> supplier) {
    return executeWithRetries("", supplier, null);
  }

  protected <T> T executeWithRetries(
      final String operationName, final CheckedSupplier<T> supplier) {
    return executeWithRetries(operationName, supplier, null);
  }

  protected <T> T executeWithRetries(
      final String operationName,
      final CheckedSupplier<T> supplier,
      final Predicate<T> retryPredicate) {
    return executeWithGivenRetries(numberOfRetries, operationName, supplier, retryPredicate);
  }

  protected <T> T executeWithGivenRetries(
      final int retries,
      final String operationName,
      final CheckedSupplier<T> operation,
      final Predicate<T> predicate) {
    try {
      final RetryPolicy<T> retryPolicy =
          new RetryPolicy<T>()
              .handle(
                  IOException.class,
                  OpenSearchException.class,
                  OpenSearchFailedShardsException.class)
              .withDelay(Duration.ofSeconds(delayIntervalInSeconds))
              .withMaxAttempts(retries)
              .onRetry(
                  e ->
                      logger.info(
                          "Retrying #{} {} due to {}",
                          e.getAttemptCount(),
                          operationName,
                          e.getLastFailure()))
              .onAbort(e -> logger.error("Abort {} by {}", operationName, e.getFailure()))
              .onRetriesExceeded(
                  e ->
                      logger.error(
                          "Retries {} exceeded for {}", e.getAttemptCount(), operationName));
      if (predicate != null) {
        retryPolicy.handleResultIf(predicate);
      }
      return Failsafe.with(retryPolicy).get(operation);
    } catch (final Exception e) {
      throw new OperateRuntimeException(
          "Couldn't execute operation "
              + operationName
              + " on opensearch for "
              + retries
              + " attempts with "
              + delayIntervalInSeconds
              + " seconds waiting.",
          e);
    }
  }

  protected GetTasksResponse task(final String id) throws IOException {
    return openSearchClient.tasks().get(t -> t.taskId(id));
  }

  protected Map<String, Info> tasksWithActions(final List<String> actions) throws IOException {
    return openSearchClient.tasks().list(l -> l.actions(actions)).tasks();
  }
}
