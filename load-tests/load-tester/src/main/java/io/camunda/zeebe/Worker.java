/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.zeebe.config.AppCfg;
import io.camunda.zeebe.config.WorkerCfg;
import io.camunda.zeebe.util.logging.ThrottledLogger;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Worker extends App {

  public static final Logger LOGGER = LoggerFactory.getLogger(Worker.class);
  private static final Logger THROTTLED_LOGGER = new ThrottledLogger(LOGGER, Duration.ofSeconds(5));
  private final WorkerCfg workerCfg;
  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(30);
  private volatile boolean running = true;

  Worker(final AppCfg config) {
    super(config);
    workerCfg = config.getWorker();
  }

  @Override
  public void run() {
    final long completionDelay = workerCfg.getCompletionDelay().toMillis();
    final var variables = readVariables(workerCfg.getPayloadPath());
    final BlockingQueue<Future<?>> requestFutures = new ArrayBlockingQueue<>(10_000);
    final CamundaClient client = createCamundaClient();
    printTopology(client);

    final ResponseChecker responseChecker = new ResponseChecker(requestFutures);
    responseChecker.start();

    // Start polling for user tasks
    scheduler.scheduleWithFixedDelay(
        () -> handleUserTasks(client, variables, completionDelay, requestFutures),
        0,
        workerCfg.getPollingDelay().toMillis(),
        TimeUnit.MILLISECONDS);

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  running = false;
                  scheduler.shutdown();
                  client.close();
                  responseChecker.close();
                }));
  }

  private void handleUserTasks(
      final CamundaClient client,
      final String variables,
      final long completionDelay,
      final BlockingQueue<Future<?>> requestFutures) {

    if (!running) {
      return;
    }

    try {
      // Search for unassigned user tasks with capacity limit
      final int randPage = (int) (Math.random() * 1000); // add rand so the 3 workers don't clash
      final SearchResponse<UserTask> searchResponse =
          client
              .newUserTaskSearchRequest()
              .filter(f -> f.state(UserTaskState.CREATED))
              .page(s -> s.from(randPage).limit(20))
              .send()
              .join();

      final List<UserTask> userTasks = searchResponse.items();
      LOGGER.debug("Found {} unassigned user tasks", userTasks.size());

      for (final UserTask userTask : userTasks) {
        scheduler.submit(
            () -> processUserTask(client, userTask, variables, completionDelay, requestFutures));
      }
    } catch (final Exception ex) {
      THROTTLED_LOGGER.error("Exception while searching for user tasks", ex);
    }
  }

  private void processUserTask(
      final CamundaClient client,
      final UserTask userTask,
      final String variables,
      final long completionDelay,
      final BlockingQueue<Future<?>> requestFutures) {

    final long startHandlingTime = System.currentTimeMillis();

    try {
      // Assign the user task to a worker
      final String assignee = hashCode() + "-" + System.nanoTime();
      LOGGER.debug("Assigning user task {} to {}", userTask.getUserTaskKey(), assignee);

      final var assignFuture =
          client.newAssignUserTaskCommand(userTask.getUserTaskKey()).assignee(assignee).send();

      assignFuture.get(10, TimeUnit.SECONDS);

      // Add completion delay to simulate real processing time
      addDelayToCompletion(completionDelay, startHandlingTime);

      // Complete the user task asynchronously
      LOGGER.debug("Completing user task {}", userTask.getUserTaskKey());
      final var completeFuture =
          client.newCompleteUserTaskCommand(userTask.getUserTaskKey()).variables(variables).send();

      requestFutures.add(completeFuture);

    } catch (final Exception ex) {
      THROTTLED_LOGGER.error(
          "Exception while processing user task {}", userTask.getUserTaskKey(), ex);
    }
  }

  private static void addDelayToCompletion(
      final long completionDelay, final long startHandlingTime) {
    try {
      final var elapsedTime = System.currentTimeMillis() - startHandlingTime;
      if (elapsedTime < completionDelay) {
        final long sleepTime = completionDelay - elapsedTime;
        LOGGER.debug("Sleep for {} ms", sleepTime);
        Thread.sleep(sleepTime);
      } else {
        LOGGER.debug(
            "Skip sleep. Elapsed time {} is larger then {} completion delay.",
            elapsedTime,
            completionDelay);
      }
    } catch (final Exception e) {
      THROTTLED_LOGGER.error("Exception on sleep with completion delay {}", completionDelay, e);
    }
  }

  private CamundaClient createCamundaClient() {
    return newClientBuilder().build();
  }

  public static void main(final String[] args) {
    createApp(Worker::new);
  }
}
