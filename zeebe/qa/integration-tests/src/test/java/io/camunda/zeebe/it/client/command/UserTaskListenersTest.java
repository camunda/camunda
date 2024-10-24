/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.client.command;

import static io.camunda.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.worker.JobWorker;
import io.camunda.zeebe.it.util.RecordingJobHandler;
import io.camunda.zeebe.it.util.ZeebeAssertHelper;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ZeebeIntegration
@AutoCloseResources
public class UserTaskListenersTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(UserTaskListenersTest.class);

  @TestZeebe
  private final TestStandaloneBroker zeebe = new TestStandaloneBroker().withRecordingExporter(true);

  @AutoCloseResource private ZeebeClient client;
  private List<JobWorker> workers;

  private ZeebeResourcesHelper resourcesHelper;

  @BeforeEach
  void init() {
    client = zeebe.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(15)).build();
    workers = new ArrayList<>();
    resourcesHelper = new ZeebeResourcesHelper(client);
  }

  @AfterEach
  void tearDown() {
    workers.forEach(JobWorker::close);
  }

  @Test
  void shouldCompleteUserTaskWithCompleteTaskListener() {
    // given
    final var userTaskKey =
        resourcesHelper.createSingleUserTask(
            t -> t.zeebeTaskListener(l -> l.complete().type("my_listener")));

    // open worker for `my_listener` task listener job
    final RecordingJobHandler jbHandler = new RecordingJobHandler();
    workers.add(client.newWorker().jobType("my_listener").handler(jbHandler).open());

    // when: invoke complete user task command
    final var completeUserTaskFuture =
        client
            .newUserTaskCompleteCommand(userTaskKey)
            .send()
            .thenAccept(
                ok -> LOGGER.info("User task with key: '{}' completed successfully.", userTaskKey))
            .exceptionally(
                throwable -> {
                  fail(
                      "Failed to complete user task with key: '%d' due to error:"
                          .formatted(userTaskKey),
                      throwable);
                  return null;
                })
            .toCompletableFuture();

    waitUntil(() -> !jbHandler.getHandledJobs().isEmpty());

    final var listenerJob = jbHandler.getHandledJob("my_listener");

    // complete task listener job
    client.newCompleteCommand(listenerJob).send().join();

    // wait for `complete` user task command completion
    completeUserTaskFuture.join();

    // then
    ZeebeAssertHelper.assertUserTaskCompleted(
        userTaskKey,
        (userTask) -> {
          assertThat(userTask.getVariables()).isEmpty();
          assertThat(userTask.getAction()).isEqualTo("complete");
        });
  }
}
