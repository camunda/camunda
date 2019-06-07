/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.data.generation.generators;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.data.generation.generators.client.SimpleEngineClient;
import org.camunda.optimize.data.generation.generators.client.dto.TaskDto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.CompletableFuture.runAsync;

@Slf4j
public class UserTaskCompleter {

  private static final int TASKS_TO_FETCH = 1000;
  private static final Random RANDOM = new Random();
  private static final OffsetDateTime OFFSET_DATE_TIME_OF_EPOCH = OffsetDateTime.from(
    Instant.EPOCH.atZone(ZoneId.of("UTC"))
  );

  private ExecutorService taskExecutorService;
  private SimpleEngineClient engineClient;
  private boolean shouldShutdown = false;
  private CountDownLatch finished = new CountDownLatch(0);
  private OffsetDateTime currentCreationDateFilter = OFFSET_DATE_TIME_OF_EPOCH;
  private Set<String> currentIterationHandledTaskIds = new HashSet<>();

  public UserTaskCompleter(SimpleEngineClient engineClient) {
    this.engineClient = engineClient;
  }

  public synchronized void startUserTaskCompletion() {
    if (finished == null || finished.getCount() == 0) {
      shouldShutdown = false;
      finished = new CountDownLatch(1);
      taskExecutorService = Executors.newFixedThreadPool(50);

      final Thread completerThread = new Thread(() -> {
        boolean allUserTasksHandled = false;
        do {
          final OffsetDateTime previousCreationDateFilter = currentCreationDateFilter;
          final Set<String> previousHandledTaskIds = currentIterationHandledTaskIds;
          try {
            final List<TaskDto> lastTimestampTasks =
              engineClient.getActiveTasksCreatedOn(currentCreationDateFilter);
            handleTasksInParallel(lastTimestampTasks);

            final List<TaskDto> currentTasksPage =
              engineClient.getActiveTasksCreatedAfter(currentCreationDateFilter, TASKS_TO_FETCH);
            allUserTasksHandled = currentTasksPage.size() == 0;
            if (!allUserTasksHandled) {
              currentCreationDateFilter = currentTasksPage.get(currentTasksPage.size() - 1).getCreated();
              currentIterationHandledTaskIds = new HashSet<>();
              handleTasksInParallel(currentTasksPage);

              log.info("Handled page of {} tasks", currentTasksPage.size());
              log.info(
                engineClient.getAllActiveTasksCountCreatedAfter(currentCreationDateFilter) + " tasks left to complete"
              );
            }
          } catch (Exception e) {
            log.error("User Task batch failed with [{}], will be retried.", e.getMessage(), e);

            currentCreationDateFilter = previousCreationDateFilter;
            currentIterationHandledTaskIds = previousHandledTaskIds;
          }
        } while (!allUserTasksHandled || !shouldShutdown);

        taskExecutorService.shutdown();

        finished.countDown();
      });
      completerThread.start();
    }
  }

  public synchronized void shutdown() {
    this.shouldShutdown = true;
  }

  public synchronized void awaitUserTaskCompletion(long timeout, TimeUnit unit) {
    try {
      this.finished.await(timeout, unit);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  private void handleTasksInParallel(final List<TaskDto> nextTasksPage) throws
                                                                        InterruptedException,
                                                                        ExecutionException {
    final CompletableFuture[] taskFutures = nextTasksPage.stream()
      .map(taskDto -> runAsync(() -> claimAndCompleteUserTask(taskDto), taskExecutorService))
      .toArray(CompletableFuture[]::new);

    CompletableFuture.allOf(taskFutures).get();
  }

  private void claimAndCompleteUserTask(TaskDto task) {
    if (isUnhandledTask(task.getId())) {
      try {
        engineClient.addOrRemoveIdentityLinks(task);
        engineClient.claimTask(task);

        if (RANDOM.nextDouble() > 0.95) {
          engineClient.unclaimTask(task);
        } else {
          if (RANDOM.nextDouble() < 0.97) {
            engineClient.completeUserTask(task);
          }
        }

        addHandledTaskId(task);
      } catch (Exception e) {
        log.error("Could not claim user task!", e);
      }
    }
  }

  private Boolean isUnhandledTask(String taskId) {
    return !currentIterationHandledTaskIds.contains(taskId);
  }

  private void addHandledTaskId(TaskDto task) {
    currentIterationHandledTaskIds.add(task.getId());
  }

}
