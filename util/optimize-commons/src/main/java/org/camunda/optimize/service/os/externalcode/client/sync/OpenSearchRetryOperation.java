/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.os.externalcode.client.sync;


import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import net.jodah.failsafe.function.CheckedSupplier;
import org.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.tasks.GetTasksResponse;
import org.opensearch.client.opensearch.tasks.Info;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.camunda.optimize.service.exceptions.ExceptionHelper.withIOException;

@Slf4j
public abstract class OpenSearchRetryOperation extends OpenSearchSyncOperation {
  public static final int UPDATE_RETRY_COUNT = 3;
  public static final int DEFAULT_DELAY_INTERVAL_IN_SECONDS = 2;

  public static final int DEFAULT_NUMBER_OF_RETRIES =
    30 * 10; // 30*10 with 2 seconds = 10 minutes retry loop
  private final int delayIntervalInSeconds = DEFAULT_DELAY_INTERVAL_IN_SECONDS;

  private final int numberOfRetries = DEFAULT_NUMBER_OF_RETRIES;

  protected OpenSearchRetryOperation(OpenSearchClient openSearchClient,
                                  final OptimizeIndexNameService indexNameService) {
    super(openSearchClient, indexNameService);
  }

  protected <T> T executeWithRetries(CheckedSupplier<T> supplier) {
    return executeWithRetries("", supplier, null);
  }

  protected <T> T executeWithRetries(String operationName, CheckedSupplier<T> supplier) {
    return executeWithRetries(operationName, supplier, null);
  }

  protected <T> T executeWithRetries(
    String operationName, CheckedSupplier<T> supplier, Predicate<T> retryPredicate) {
    return executeWithGivenRetries(numberOfRetries, operationName, supplier, retryPredicate);
  }

  protected <T> T executeWithGivenRetries(
    int retries, String operationName, CheckedSupplier<T> operation, Predicate<T> predicate) {
    try {
      final RetryPolicy<T> retryPolicy =
        new RetryPolicy<T>()
          .handle(IOException.class, OpenSearchException.class)
          .withDelay(Duration.ofSeconds(delayIntervalInSeconds))
          .withMaxAttempts(retries)
          .onRetry(
            e ->
              log.info(
                "Retrying #{} {} due to {}",
                e.getAttemptCount(),
                operationName,
                e.getLastFailure()))
          .onAbort(e -> log.error("Abort {} by {}", operationName, e.getFailure()))
          .onRetriesExceeded(
            e ->
              log.error(
                "Retries {} exceeded for {}", e.getAttemptCount(), operationName));
      if (predicate != null) {
        retryPolicy.handleResultIf(predicate);
      }
      return Failsafe.with(retryPolicy).get(operation);
    } catch (Exception e) {
      throw new OptimizeRuntimeException(
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

  protected GetTasksResponse task(String id) throws IOException {
    return openSearchClient.tasks().get(t -> t.taskId(id));
  }

  protected Map<String, Info> tasksWithActions(List<String> actions) throws IOException {
    return openSearchClient.tasks().list(l -> l.actions(actions)).tasks();
  }

  protected GetTasksResponse waitTaskCompletion(String taskId) {
    final String[] taskIdParts = taskId.split(":");
    final String nodeId = taskIdParts[0];
    final long id = Long.parseLong(taskIdParts[1]);
    return executeWithGivenRetries(
      Integer.MAX_VALUE,
      "GetTaskInfo{" + nodeId + "},{" + id + "}",
      () -> {
        checkTaskErrorsOrFailures(nodeId, (int) id);
        return task(taskId);
      },
      this::needsToPollAgain
    );
  }

  private void checkTaskErrorsOrFailures(final String node, final Integer id) throws IOException {
    final GetTasksResponse tasks = withIOException(() -> task(node + ":" + id));

    if (tasks != null) {
      checkForErrors(tasks);
      checkForFailures(tasks);
    }
  }

  private void checkForErrors(final GetTasksResponse taskResponse) {
    if (taskResponse.error() != null) {
      throw new OptimizeRuntimeException(taskResponse.error().reason());
    }
  }

  private void checkForFailures(final GetTasksResponse taskResponse) {
    if (taskResponse.response().failures() != null) {
      throw new OptimizeRuntimeException(taskResponse.response().failures().get(0));
    }
  }

  private boolean needsToPollAgain(final GetTasksResponse taskResponse) {
    var r = taskResponse.response();
    var allTasksExecuted = r.total() == r.created() + r.updated() + r.deleted();
    return !(taskResponse.completed() && allTasksExecuted);
  }
}
