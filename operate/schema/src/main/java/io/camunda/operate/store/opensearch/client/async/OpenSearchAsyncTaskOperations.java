/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.opensearch.client.async;

import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.util.BackoffIdleStrategy;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.tasks.GetTasksResponse;
import org.slf4j.Logger;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

public class OpenSearchAsyncTaskOperations extends OpenSearchAsyncOperation {
  public OpenSearchAsyncTaskOperations(Logger logger, OpenSearchAsyncClient openSearchAsyncClient) {
    super(logger, openSearchAsyncClient);
  }

  private static String defaultTaskErrorMessage(String id) {
    return String.format("Failed to fetch task %s", id);
  }

  public CompletableFuture<GetTasksResponse> task(String id) {
    return safe(
        () -> openSearchAsyncClient.tasks().get(b -> b.taskId(id)),
        e -> defaultTaskErrorMessage(id));
  }

  public CompletableFuture<Long> totalImpactedByTask(
      String taskId, ThreadPoolTaskScheduler executor) {
    final CompletableFuture<Long> result = new CompletableFuture<>();

    final BackoffIdleStrategy idleStrategy = new BackoffIdleStrategy(1_000, 1.2f, 5_000);
    final Runnable checkTaskResultRunnable =
        new Runnable() {
          @Override
          public void run() {
            try {
              task(taskId)
                  .whenComplete(
                      (response, e) -> {
                        if (e != null) {
                          result.completeExceptionally(
                              new OperateRuntimeException("Task not found: " + taskId, e));
                        } else {
                          if (response.completed()) {
                            final var status = response.task().status();

                            if (status.created() + status.updated() + status.deleted()
                                < status.total()) {
                              // there were some failures
                              final String errorMsg =
                                  String.format(
                                      "Failures occurred during task %s execution! Check Opensearch logs.",
                                      taskId);
                              throw new OperateRuntimeException(errorMsg);
                            }
                            logger.debug("Task {} succeeded.", taskId);
                            result.complete(status.total());
                          } else {
                            idleStrategy.idle();
                            executor.schedule(
                                this, Date.from(Instant.now().plusMillis(idleStrategy.idleTime())));
                          }
                        }
                      });
            } catch (Exception e) {
              result.completeExceptionally(e);
            }
          }
        };

    executor.submit(checkTaskResultRunnable);

    return result;
  }
}
