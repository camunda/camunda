/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.utils;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.UserTask;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.awaitility.Awaitility;

public class CamundaClientTestRequestHelper {

  public static List<UserTask> getUserTasksForProcessInstance(
      final CamundaClient client, final long processInstanceId) {
    return client
        .newUserTaskQuery()
        .filter(f -> f.processInstanceKey(processInstanceId))
        .send()
        .join()
        .items();
  }

  public static long deployProcessAndStartInstance(
      final CamundaClient client,
      final String resourceClassPath,
      final String processDefinitionId) {
    client.newDeployResourceCommand().addResourceFromClasspath(resourceClassPath).send().join();
    waitForProcessToBeDeployed(client, processDefinitionId);
    return startProcessInstance(client, processDefinitionId);
  }

  public static void waitForProcessToBeDeployed(
      final CamundaClient client, final String processDefinitionId) {
    Awaitility.await("should deploy and export process")
        .atMost(Duration.ofSeconds(15))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result =
                  client
                      .newProcessDefinitionQuery()
                      .filter(f -> f.processDefinitionId(processDefinitionId))
                      .send()
                      .join();
              assertThat(result.items().size()).isEqualTo(1);
            });
  }

  public static long startProcessInstance(
      final CamundaClient client, final String processDefinitionId) {
    return client
        .newCreateInstanceCommand()
        .bpmnProcessId(processDefinitionId)
        .latestVersion()
        .send()
        .join()
        .getProcessInstanceKey();
  }

  public static void failJobAndWaitForIncident(
      final CamundaClient client, final long jobKey, final long processInstanceKey) {
    client.newFailCommand(jobKey).retries(0).errorMessage("failed with no retries").send().join();
    waitForIncident(client, processInstanceKey);
  }

  public static void waitForIncident(final CamundaClient client, final long processInstanceKey) {
    Awaitility.await("should find incident for process instance")
        .atMost(Duration.ofSeconds(15))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var response =
                  client.newProcessInstanceGetRequest(processInstanceKey).send().join();
              assertThat(response.getHasIncident()).isTrue();
            });
  }

  public static long waitForJobActivation(final CamundaClient client, final String jobType) {
    final AtomicLong jobKey = new AtomicLong();
    Awaitility.await("should activate job of job type")
        .atMost(Duration.ofSeconds(15))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var response =
                  client
                      .newActivateJobsCommand()
                      .jobType(jobType)
                      .maxJobsToActivate(1)
                      .send()
                      .join();
              assertThat(response.getJobs().isEmpty()).isFalse();
              jobKey.set(response.getJobs().getFirst().getKey());
            });
    return jobKey.longValue();
  }

  public static void waitForIncidentResolved(
      final CamundaClient client, final long processInstanceKey) {
    Awaitility.await("should find no more incident for process ")
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              final var response =
                  client.newProcessInstanceGetRequest(processInstanceKey).send().join();
              assertThat(response.getHasIncident()).isFalse();
            });
  }
}
