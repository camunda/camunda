/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.util;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.command.ClientException;
import io.zeebe.client.api.command.CompleteJobCommandStep1;
import io.zeebe.client.api.command.CreateWorkflowInstanceCommandStep1;
import io.zeebe.client.api.command.DeployWorkflowCommandStep1;
import io.zeebe.client.api.command.FailJobCommandStep1.FailJobCommandStep2;
import io.zeebe.client.api.response.ActivatedJob;
import io.zeebe.client.api.response.DeploymentEvent;
import io.zeebe.client.api.response.WorkflowInstanceEvent;
import io.zeebe.client.api.worker.JobClient;
import io.zeebe.model.bpmn.BpmnModelInstance;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ZeebeTestUtil {

  private static final Logger logger = LoggerFactory.getLogger(ZeebeTestUtil.class);

  public static final Logger ALL_EVENTS_LOGGER = LoggerFactory.getLogger("org.camunda.operate.ALL_EVENTS");

  /**
   * Deploys the process synchronously.
   * @param client
   * @param classpathResources
   * @return workflow id
   */
  public static Long deployWorkflow(ZeebeClient client, String... classpathResources) {
    if (classpathResources.length == 0) {
      return null;
    }
    DeployWorkflowCommandStep1 deployWorkflowCommandStep1 = client.newDeployCommand();
    for (String classpathResource: classpathResources) {
      deployWorkflowCommandStep1 = deployWorkflowCommandStep1.addResourceFromClasspath(classpathResource);
    }
    final DeploymentEvent deploymentEvent =
      ((DeployWorkflowCommandStep1.DeployWorkflowCommandBuilderStep2)deployWorkflowCommandStep1)
        .send()
        .join();
    logger.debug("Deployment of resource [{}] was performed", (Object[])classpathResources);
    return deploymentEvent.getWorkflows().get(classpathResources.length - 1).getWorkflowKey();
  }

  /**
   * Deploys the process synchronously.
   * @param client
   * @param workflowModel
   * @return workflow id
   */
  public static Long deployWorkflow(ZeebeClient client, BpmnModelInstance workflowModel, String resourceName) {
    DeployWorkflowCommandStep1 deployWorkflowCommandStep1 = client.newDeployCommand()
      .addWorkflowModel(workflowModel, resourceName);
    final DeploymentEvent deploymentEvent =
      ((DeployWorkflowCommandStep1.DeployWorkflowCommandBuilderStep2)deployWorkflowCommandStep1)
        .send()
        .join();
    logger.debug("Deployment of resource [{}] was performed", resourceName);
    return deploymentEvent.getWorkflows().get(0).getWorkflowKey();
  }

  /**
   *
   * @param client
   * @param bpmnProcessId
   * @param payload
   * @return workflow instance id
   */
  public static long startWorkflowInstance(ZeebeClient client, String bpmnProcessId, String payload) {
    final CreateWorkflowInstanceCommandStep1.CreateWorkflowInstanceCommandStep3 createWorkflowInstanceCommandStep3 = client
      .newCreateInstanceCommand().bpmnProcessId(bpmnProcessId).latestVersion();
    if (payload != null) {
      createWorkflowInstanceCommandStep3.variables(payload);
    }
    WorkflowInstanceEvent workflowInstanceEvent = null;
    try {
      workflowInstanceEvent =
        createWorkflowInstanceCommandStep3
        .send().join();
      logger.debug("Workflow instance created for workflow [{}]", bpmnProcessId);
    } catch (ClientException ex) {
      //retry once
      try {
        Thread.sleep(300L);
      } catch (InterruptedException e) {
        logger.error(String.format("Error occurred when starting workflow instance for bpmnProcessId [%s]: [%s]. Retrying...", bpmnProcessId, ex.getMessage()), ex);
      }
      workflowInstanceEvent =
        createWorkflowInstanceCommandStep3
          .send().join();
      logger.debug("Workflow instance created for workflow [{}]", bpmnProcessId);
    }
    return workflowInstanceEvent.getWorkflowInstanceKey();
  }

  public static void cancelWorkflowInstance(ZeebeClient client, long workflowInstanceKey) {
    client.newCancelInstanceCommand(workflowInstanceKey).send().join();

  }

  public static void completeTask(ZeebeClient client, String jobType, String workerName, String payload) {
    completeTask(client, jobType, workerName, payload, 1);
  }
  public static void completeTask(ZeebeClient client, String jobType, String workerName, String payload, int count) {
    handleTasks(client, jobType, workerName, count, (jobClient, job) -> {
      CompleteJobCommandStep1 command = jobClient.newCompleteCommand(job.getKey());
      if (payload != null) {
        command.variables(payload);
      }
      command.send().join();
    });
  }

  public static Long failTask(ZeebeClient client, String jobType, String workerName, int numberOfFailures, String errorMessage) {
    return handleTasks(client, jobType, workerName, numberOfFailures, ((jobClient, job) -> {
      FailJobCommandStep2 failCommand = jobClient.newFailCommand(job.getKey())
          .retries(job.getRetries() - 1);
      if (errorMessage != null) {
        failCommand.errorMessage(errorMessage);
      }
      failCommand.send().join();
    })).get(0);
  }

  private static List<Long> handleTasks(ZeebeClient client, String jobType, String workerName, int jobCount, BiConsumer<JobClient, ActivatedJob> jobHandler) {
    final List<Long> jobKeys = new ArrayList<>();
    while (jobKeys.size() < jobCount) {
      client.newActivateJobsCommand()
          .jobType(jobType)
          .maxJobsToActivate(jobCount - jobKeys.size())
          .workerName(workerName)
          .timeout(Duration.ofSeconds(2))
          .send()
          .join()
          .getJobs()
          .forEach(job -> {
            jobHandler.accept(client, job);
            jobKeys.add(job.getKey());
          });
    }
    return jobKeys;
  }

  public static void resolveIncident(ZeebeClient client, Long jobKey, Long incidentKey) {
    client.newUpdateRetriesCommand(jobKey).retries(3).send().join();
    client.newResolveIncidentCommand(incidentKey).send().join();
  }

  public static void updateVariables(ZeebeClient client, Long scopeKey, String newPayload) {
    client.newSetVariablesCommand(scopeKey).variables(newPayload).local(true).send().join();
  }

}
