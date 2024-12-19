/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util;

import static io.camunda.operate.util.ThreadUtil.sleepFor;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.command.CompleteJobCommandStep1;
import io.camunda.client.api.command.CreateProcessInstanceCommandStep1;
import io.camunda.client.api.command.DeployResourceCommandStep1;
import io.camunda.client.api.command.DeployResourceCommandStep1.DeployResourceCommandStep2;
import io.camunda.client.api.command.FailJobCommandStep1.FailJobCommandStep2;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.worker.JobClient;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ZeebeTestUtil {

  public static final Logger ALL_EVENTS_LOGGER =
      LoggerFactory.getLogger("io.camunda.operate.ALL_EVENTS");
  private static final Logger LOGGER = LoggerFactory.getLogger(ZeebeTestUtil.class);

  /**
   * Deploys the process synchronously.
   *
   * @param client client
   * @param classpathResources classpath resources
   * @return process id
   */
  public static Long deployProcess(
      final CamundaClient client, final String tenantId, final String... classpathResources) {
    return deployProcess(false, client, tenantId, classpathResources);
  }

  public static Long deployProcess(
      final boolean ignoreException,
      final CamundaClient client,
      final String tenantId,
      final String... classpathResources) {
    try {
      if (classpathResources.length == 0) {
        return null;
      }
      DeployResourceCommandStep1 deployProcessCommandStep1 = client.newDeployResourceCommand();
      for (final String classpathResource : classpathResources) {
        deployProcessCommandStep1 =
            deployProcessCommandStep1.addResourceFromClasspath(classpathResource);
      }
      if (tenantId != null) {
        deployProcessCommandStep1 =
            ((DeployResourceCommandStep1.DeployResourceCommandStep2) deployProcessCommandStep1)
                .tenantId(tenantId);
      }
      final DeploymentEvent deploymentEvent =
          ((DeployResourceCommandStep1.DeployResourceCommandStep2) deployProcessCommandStep1)
              .send()
              .join();
      LOGGER.debug("Deployment of resource [{}] was performed", (Object[]) classpathResources);
      return deploymentEvent
          .getProcesses()
          .get(classpathResources.length - 1)
          .getProcessDefinitionKey();
    } catch (final Exception e) {
      if (ignoreException) {
        LOGGER.warn("Deployment failed: " + e.getMessage());
        return null;
      } else {
        throw e;
      }
    }
  }

  public static void deployDecision(
      final CamundaClient client, final String tenantId, final String... classpathResources) {
    if (classpathResources.length == 0) {
      return;
    }
    DeployResourceCommandStep1 deployProcessCommandStep1 = client.newDeployResourceCommand();
    for (final String classpathResource : classpathResources) {
      deployProcessCommandStep1 =
          deployProcessCommandStep1.addResourceFromClasspath(classpathResource);
    }
    if (tenantId != null) {
      deployProcessCommandStep1 =
          ((DeployResourceCommandStep1.DeployResourceCommandStep2) deployProcessCommandStep1)
              .tenantId(tenantId);
    }
    ((DeployResourceCommandStep1.DeployResourceCommandStep2) deployProcessCommandStep1)
        .send()
        .join();
    LOGGER.debug("Deployment of resource [{}] was performed", (Object[]) classpathResources);
  }

  /**
   * Deploys the process synchronously.
   *
   * @param client client
   * @param processModel processModel
   * @param resourceName resourceName
   * @return process id
   */
  public static Long deployProcess(
      final CamundaClient client,
      final String tenantId,
      final BpmnModelInstance processModel,
      final String resourceName) {
    DeployResourceCommandStep2 deployProcessCommandStep1 =
        client.newDeployResourceCommand().addProcessModel(processModel, resourceName);
    if (tenantId != null) {
      deployProcessCommandStep1 = deployProcessCommandStep1.tenantId(tenantId);
    }
    final DeploymentEvent deploymentEvent = deployProcessCommandStep1.send().join();
    LOGGER.debug("Deployment of resource [{}] was performed", resourceName);
    return deploymentEvent.getProcesses().get(0).getProcessDefinitionKey();
  }

  public static long startProcessInstance(
      final CamundaClient client, final String bpmnProcessId, final String payload) {
    return startProcessInstance(client, null, bpmnProcessId, payload);
  }

  /**
   * @param client client
   * @param bpmnProcessId bpmnProcessId
   * @param payload payload
   * @return process instance id
   */
  public static long startProcessInstance(
      final CamundaClient client,
      final String tenantId,
      final String bpmnProcessId,
      final String payload) {
    return startProcessInstance(false, client, tenantId, bpmnProcessId, payload);
  }

  public static long startProcessInstance(
      final boolean ignoreException,
      final CamundaClient client,
      final String tenantId,
      final String bpmnProcessId,
      final String payload) {
    return startProcessInstance(false, client, tenantId, bpmnProcessId, null, payload);
  }

  public static long startProcessInstance(
      final boolean ignoreException,
      final CamundaClient client,
      final String tenantId,
      final String bpmnProcessId,
      final Integer processVersion,
      final String payload) {
    try {
      final CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3
          createProcessInstanceCommandStep3 =
              (processVersion == null)
                  ? client.newCreateInstanceCommand().bpmnProcessId(bpmnProcessId).latestVersion()
                  : client
                      .newCreateInstanceCommand()
                      .bpmnProcessId(bpmnProcessId)
                      .version(processVersion);
      if (tenantId != null) {
        createProcessInstanceCommandStep3.tenantId(tenantId);
      }
      if (payload != null) {
        createProcessInstanceCommandStep3.variables(payload);
      }
      ProcessInstanceEvent processInstanceEvent = null;
      try {
        processInstanceEvent = createProcessInstanceCommandStep3.send().join();
        LOGGER.debug("Process instance created for process [{}]", bpmnProcessId);
      } catch (final ClientException ex) {
        // retry once
        sleepFor(300L);
        processInstanceEvent = createProcessInstanceCommandStep3.send().join();
        LOGGER.debug("Process instance created for process [{}]", bpmnProcessId);
      }
      return processInstanceEvent.getProcessInstanceKey();
    } catch (final Exception e) {
      if (ignoreException) {
        LOGGER.warn("Instance creation failed: " + e.getMessage());
        return 0L;
      } else {
        throw e;
      }
    }
  }

  public static void cancelProcessInstance(
      final boolean ignoreException, final CamundaClient client, final long processInstanceKey) {
    try {
      client.newCancelInstanceCommand(processInstanceKey).send().join();
    } catch (final Exception e) {
      if (!ignoreException) {
        throw e;
      } else {
        LOGGER.warn("Cancellation failed: " + e.getMessage());
      }
    }
  }

  public static void cancelProcessInstance(
      final CamundaClient client, final long processInstanceKey) {
    cancelProcessInstance(false, client, processInstanceKey);
  }

  public static void completeTask(
      final CamundaClient client,
      final String jobType,
      final String workerName,
      final String payload) {
    completeTask(client, jobType, workerName, payload, 1);
  }

  public static void completeTask(
      final CamundaClient client,
      final String jobType,
      final String workerName,
      final String payload,
      final int count) {
    handleTasks(
        client,
        jobType,
        workerName,
        count,
        (jobClient, job) -> {
          final CompleteJobCommandStep1 command = jobClient.newCompleteCommand(job.getKey());
          if (payload != null) {
            command.variables(payload);
          }
          command.send().join();
        });
  }

  public static Long failTask(
      final CamundaClient client,
      final String jobType,
      final String workerName,
      final int numberOfFailures,
      final String errorMessage) {
    return handleTasks(
            client,
            jobType,
            workerName,
            numberOfFailures,
            ((jobClient, job) -> {
              final FailJobCommandStep2 failCommand =
                  jobClient.newFailCommand(job.getKey()).retries(job.getRetries() - 1);
              if (errorMessage != null) {
                failCommand.errorMessage(errorMessage);
              }
              failCommand.send().join();
            }))
        .get(0);
  }

  public static Long failTaskWithRetriesLeft(
      final CamundaClient client,
      final String jobType,
      final String workerName,
      final int numberOfRetriesLeft,
      final String errorMessage) {
    return handleTasks(
            client,
            jobType,
            workerName,
            1,
            ((jobClient, job) -> {
              final FailJobCommandStep2 failCommand =
                  jobClient.newFailCommand(job.getKey()).retries(numberOfRetriesLeft);
              if (errorMessage != null) {
                failCommand.errorMessage(errorMessage);
              }
              failCommand.send().join();
            }))
        .get(0);
  }

  public static Long throwErrorInTask(
      final CamundaClient client,
      final String jobType,
      final String workerName,
      final int numberOfFailures,
      final String errorCode,
      final String errorMessage) {
    return handleTasks(
            client,
            jobType,
            workerName,
            numberOfFailures,
            ((jobClient, job) -> {
              jobClient
                  .newThrowErrorCommand(job.getKey())
                  .errorCode(errorCode)
                  .errorMessage(errorMessage)
                  .send()
                  .join();
            }))
        .get(0);
  }

  private static List<Long> handleTasks(
      final CamundaClient client,
      final String jobType,
      final String workerName,
      final int jobCount,
      final BiConsumer<JobClient, ActivatedJob> jobHandler) {
    final List<Long> jobKeys = new ArrayList<>();
    while (jobKeys.size() < jobCount) {
      client
          .newActivateJobsCommand()
          .jobType(jobType)
          .maxJobsToActivate(jobCount - jobKeys.size())
          .workerName(workerName)
          .timeout(Duration.ofSeconds(2))
          .send()
          .join()
          .getJobs()
          .forEach(
              job -> {
                jobHandler.accept(client, job);
                jobKeys.add(job.getKey());
              });
    }
    return jobKeys;
  }

  public static void resolveIncident(
      final CamundaClient client, final Long jobKey, final Long incidentKey) {
    client.newUpdateRetriesCommand(jobKey).retries(3).send().join();
    client.newResolveIncidentCommand(incidentKey).send().join();
  }

  public static void updateVariables(
      final CamundaClient client, final Long scopeKey, final String newPayload) {
    client.newSetVariablesCommand(scopeKey).variables(newPayload).local(true).send().join();
  }

  public static void sendMessages(
      final CamundaClient client,
      final String messageName,
      final String payload,
      final int count,
      final String correlationKey) {
    for (int i = 0; i < count; i++) {
      client
          .newPublishMessageCommand()
          .messageName(messageName)
          .correlationKey(correlationKey)
          .variables(payload)
          .timeToLive(Duration.ofSeconds(30))
          .messageId(UUID.randomUUID().toString())
          .send()
          .join();
    }
  }

  public static void sendSignal(
      final CamundaClient client, final String signalName, final String payload, final int count) {
    for (int i = 0; i < count; i++) {
      client.newBroadcastSignalCommand().signalName(signalName).variables(payload).send().join();
    }
  }
}
