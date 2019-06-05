/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.qa.util;

import java.time.Duration;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.commands.CreateWorkflowInstanceCommandStep1;
import io.zeebe.client.api.commands.DeployWorkflowCommandStep1;
import io.zeebe.client.api.events.DeploymentEvent;
import io.zeebe.client.api.events.WorkflowInstanceEvent;
import io.zeebe.client.api.subscription.JobWorker;
import io.zeebe.model.bpmn.BpmnModelInstance;

public abstract class ZeebeTestUtil {

  private static final Logger logger = LoggerFactory.getLogger(ZeebeTestUtil.class);

  private static Random random = new Random();

  public static String deployWorkflow(ZeebeClient client, BpmnModelInstance workflowModel, String resourceName) {
    DeployWorkflowCommandStep1 deployWorkflowCommandStep1 = client.newDeployCommand()
      .addWorkflowModel(workflowModel, resourceName);
    final DeploymentEvent deploymentEvent =
      ((DeployWorkflowCommandStep1.DeployWorkflowCommandBuilderStep2)deployWorkflowCommandStep1)
        .send()
        .join();
    logger.debug("Deployment of resource [{}] was performed", resourceName);
    return String.valueOf(deploymentEvent.getWorkflows().get(0).getWorkflowKey());
  }

  public static void startWorkflowInstanceAsync(ZeebeClient client, String bpmnProcessId, String payload) {
    final CreateWorkflowInstanceCommandStep1.CreateWorkflowInstanceCommandStep3 createWorkflowInstanceCommandStep3 = client
      .newCreateInstanceCommand().bpmnProcessId(bpmnProcessId).latestVersion();
    if (payload != null) {
      createWorkflowInstanceCommandStep3.variables(payload);
    }
    createWorkflowInstanceCommandStep3.send();
    logger.debug("Create workflow instance command was sent to Zeebe, workflow [{}]", bpmnProcessId);
  }

  public static void completeTask(ZeebeClient client, String jobType, String workerName, String payload, int count) {
    final int[] countCompleted = { 0 };
    JobWorker jobWorker = client.newWorker()
      .jobType(jobType)
      .handler((jobClient, job) -> {
        if (countCompleted[0] < count) {
          jobClient.newCompleteCommand(job.getKey()).variables(payload).send();
          logger.debug("Task completed jobKey [{}]", job.getKey());
          countCompleted[0]++;
          if (countCompleted[0] % 1000 == 0) {
            logger.info("{} jobs completed ", countCompleted[0]);
          }
        }
      })
      .name(workerName)
      .timeout(Duration.ofSeconds(2))
      .open();
    //wait till all requested tasks are completed
    while(countCompleted[0] < count) {
      try {
        Thread.sleep(1000L);
      } catch (InterruptedException e) {
      }
    }
    jobWorker.close();
  }

  public static void failTask(ZeebeClient client, String jobType, String workerName, int incidentCount) {
    failTask(client, jobType, workerName, null, incidentCount);
  }

  public static void failTask(ZeebeClient client, String jobType, String workerName, String errorMessage, int incidentCount) {
    final int[] countFailed = { 0 };
    JobWorker jobWorker = client.newWorker()
      .jobType(jobType)
      .handler((jobClient, activatedJob) -> {
        final String error = errorMessage == null ? "Error " + random.nextInt(50) : errorMessage;
        if (countFailed[0] < incidentCount) {
          client.newFailCommand(activatedJob.getKey()).retries(0).errorMessage(error).send();
          countFailed[0]++;
          if (countFailed[0] % 1000 == 0) {
            logger.info("{} jobs failed ", countFailed[0]);
          }
        }
      })
      .name(workerName)
      .timeout(Duration.ofSeconds(2))
      .open();
    //wait till all incidents are created
    while (countFailed[0] < incidentCount) {
      try {
        Thread.sleep(200L);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
    jobWorker.close();
  }

}
