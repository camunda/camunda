/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.qa.util;

import static io.camunda.operate.util.ThreadUtil.sleepFor;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.command.CompleteJobCommandStep1;
import io.camunda.client.api.command.CreateProcessInstanceCommandStep1;
import io.camunda.client.api.command.DeployProcessCommandStep1;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.worker.JobWorker;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.time.Duration;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ZeebeTestUtil {

  private static final Logger LOGGER = LoggerFactory.getLogger(ZeebeTestUtil.class);

  private static final Random RANDOM = new Random();

  public static String deployProcess(
      final CamundaClient client, final String... classpathResources) {
    if (classpathResources.length == 0) {
      return null;
    }
    DeployProcessCommandStep1 deployProcessCommandStep1 = client.newDeployCommand();
    for (final String classpathResource : classpathResources) {
      deployProcessCommandStep1 =
          deployProcessCommandStep1.addResourceFromClasspath(classpathResource);
    }
    final DeploymentEvent deploymentEvent =
        ((DeployProcessCommandStep1.DeployProcessCommandBuilderStep2) deployProcessCommandStep1)
            .send()
            .join();
    LOGGER.debug("Deployment of resource [{}] was performed", (Object[]) classpathResources);
    return String.valueOf(
        deploymentEvent
            .getProcesses()
            .get(classpathResources.length - 1)
            .getProcessDefinitionKey());
  }

  public static void deployDecision(
      final CamundaClient client, final String... classpathResources) {
    if (classpathResources.length == 0) {
      return;
    }
    DeployProcessCommandStep1 deployProcessCommandStep1 = client.newDeployCommand();
    for (final String classpathResource : classpathResources) {
      deployProcessCommandStep1 =
          deployProcessCommandStep1.addResourceFromClasspath(classpathResource);
    }
    final DeploymentEvent deploymentEvent =
        ((DeployProcessCommandStep1.DeployProcessCommandBuilderStep2) deployProcessCommandStep1)
            .send()
            .join();
    LOGGER.debug("Deployment of resource [{}] was performed", (Object[]) classpathResources);
  }

  public static String deployProcess(
      final CamundaClient client, final BpmnModelInstance processModel, final String resourceName) {
    final DeployProcessCommandStep1 deployProcessCommandStep1 =
        client.newDeployCommand().addProcessModel(processModel, resourceName);
    final DeploymentEvent deploymentEvent =
        ((DeployProcessCommandStep1.DeployProcessCommandBuilderStep2) deployProcessCommandStep1)
            .send()
            .join();
    LOGGER.debug("Deployment of resource [{}] was performed", resourceName);
    return String.valueOf(deploymentEvent.getProcesses().get(0).getProcessDefinitionKey());
  }

  public static CamundaFuture<ProcessInstanceEvent> startProcessInstanceAsync(
      final CamundaClient client, final String bpmnProcessId, final String payload) {
    final CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3
        createProcessInstanceCommandStep3 =
            client.newCreateInstanceCommand().bpmnProcessId(bpmnProcessId).latestVersion();
    if (payload != null) {
      createProcessInstanceCommandStep3.variables(payload);
    }
    return createProcessInstanceCommandStep3.send();
  }

  public static long startProcessInstance(
      final CamundaClient client, final String bpmnProcessId, final String payload) {
    final CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3
        createProcessInstanceCommandStep3 =
            client.newCreateInstanceCommand().bpmnProcessId(bpmnProcessId).latestVersion();
    if (payload != null) {
      createProcessInstanceCommandStep3.variables(payload);
    }
    final ProcessInstanceEvent processInstanceEvent =
        createProcessInstanceCommandStep3.send().join();
    LOGGER.debug("Process instance created for process [{}]", bpmnProcessId);
    return processInstanceEvent.getProcessInstanceKey();
  }

  public static void completeTask(
      final CamundaClient client,
      final String jobType,
      final String workerName,
      final String payload,
      final int count) {
    final int[] countCompleted = {0};
    final JobWorker jobWorker =
        client
            .newWorker()
            .jobType(jobType)
            .handler(
                (jobClient, job) -> {
                  try {
                    if (countCompleted[0] < count) {
                      CompleteJobCommandStep1 completeJobCommandStep1 =
                          jobClient.newCompleteCommand(job.getKey());
                      if (payload != null) {
                        completeJobCommandStep1 = completeJobCommandStep1.variables(payload);
                      }
                      completeJobCommandStep1.send().join();
                      LOGGER.debug("Task completed jobKey [{}]", job.getKey());
                      countCompleted[0]++;
                      if (countCompleted[0] % 1000 == 0) {
                        LOGGER.info("{} jobs completed ", countCompleted[0]);
                      }
                    }
                  } catch (final Exception ex) {
                    LOGGER.error(ex.getMessage(), ex);
                    throw ex;
                  }
                })
            .name(workerName)
            .timeout(Duration.ofSeconds(2))
            .open();
    // wait till all requested tasks are completed
    while (countCompleted[0] < count) {
      sleepFor(1000);
    }
    jobWorker.close();
  }

  public static void failTask(
      final CamundaClient client,
      final String jobType,
      final String workerName,
      final int incidentCount) {
    failTask(client, jobType, workerName, null, incidentCount);
  }

  public static void failTask(
      final CamundaClient client,
      final String jobType,
      final String workerName,
      final String errorMessage,
      final int incidentCount) {
    final int[] countFailed = {0};
    final JobWorker jobWorker =
        client
            .newWorker()
            .jobType(jobType)
            .handler(
                (jobClient, activatedJob) -> {
                  final String error =
                      errorMessage == null ? "Error " + RANDOM.nextInt(50) : errorMessage;
                  if (countFailed[0] < incidentCount) {
                    client
                        .newFailCommand(activatedJob.getKey())
                        .retries(0)
                        .errorMessage(error)
                        .send()
                        .join();
                    countFailed[0]++;
                    if (countFailed[0] % 1000 == 0) {
                      LOGGER.info("{} jobs failed ", countFailed[0]);
                    }
                  }
                })
            .name(workerName)
            .timeout(Duration.ofSeconds(2))
            .open();
    // wait till all incidents are created
    while (countFailed[0] < incidentCount) {
      sleepFor(200);
    }
    jobWorker.close();
  }
}
