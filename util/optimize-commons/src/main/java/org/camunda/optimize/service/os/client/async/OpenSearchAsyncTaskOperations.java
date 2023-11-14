/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.os.client.async;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.os.util.BackoffIdleStrategy;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.tasks.GetTasksResponse;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Instant;
import java.util.Date;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class OpenSearchAsyncTaskOperations extends OpenSearchAsyncOperation {
  public OpenSearchAsyncTaskOperations(OpenSearchAsyncClient openSearchAsyncClient,
                                       OptimizeIndexNameService indexNameService) {
    super(openSearchAsyncClient, indexNameService);
  }

  private static String defaultTaskErrorMessage(String id) {
    return String.format("Failed to fetch task %s", id);
  }

  public CompletableFuture<GetTasksResponse> task(String id) {
    return safe(() -> openSearchAsyncClient.tasks().get(b -> b.taskId(id)), e -> defaultTaskErrorMessage(id));
  }

  public CompletableFuture<Long> totalImpactedByTask(String taskId, ThreadPoolTaskScheduler executor) {
    CompletableFuture<Long> result = new CompletableFuture<>();

    final BackoffIdleStrategy idleStrategy = new BackoffIdleStrategy(1_000, 1.2f, 5_000);
    final Runnable checkTaskResultRunnable = new Runnable() {
      @Override
      public void run() {
        try {
          task(taskId).whenComplete((response, e) -> {
            if(e != null){
              result.completeExceptionally(new OptimizeRuntimeException("Task not found: " + taskId, e));
            } else {
              if (response.completed()) {
                var status = response.task().status();

                if (status.created() + status.updated() + status.deleted() < status.total()) {
                  //there were some failures
                  final String errorMsg = String.format("Failures occurred during task %s execution! Check OpenSearch logs.", taskId);
                  throw new OptimizeRuntimeException(errorMsg);
                }
                log.debug("Task {} succeeded.", taskId);
                result.complete(status.total());
              } else {
                idleStrategy.idle();
                executor.schedule(this, Date.from(Instant.now().plusMillis(idleStrategy.idleTime())));
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
