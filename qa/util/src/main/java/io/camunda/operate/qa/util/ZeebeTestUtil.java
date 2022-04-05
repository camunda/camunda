/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.qa.util;

import static io.camunda.operate.util.ThreadUtil.sleepFor;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.command.CompleteJobCommandStep1;
import io.camunda.zeebe.client.api.command.CreateProcessInstanceCommandStep1;
import io.camunda.zeebe.client.api.command.DeployProcessCommandStep1;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.client.api.worker.JobWorker;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.time.Duration;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ZeebeTestUtil {

  private static final Logger logger = LoggerFactory.getLogger(ZeebeTestUtil.class);

  private static Random random = new Random();

  public static String deployProcess(ZeebeClient client, String... classpathResources) {
    if (classpathResources.length == 0) {
      return null;
    }
    DeployProcessCommandStep1 deployProcessCommandStep1 = client.newDeployCommand();
    for (String classpathResource: classpathResources) {
      deployProcessCommandStep1 = deployProcessCommandStep1.addResourceFromClasspath(classpathResource);
    }
    final DeploymentEvent deploymentEvent =
        ((DeployProcessCommandStep1.DeployProcessCommandBuilderStep2)deployProcessCommandStep1)
            .send()
            .join();
    logger.debug("Deployment of resource [{}] was performed", (Object[])classpathResources);
    return String.valueOf(
        deploymentEvent.getProcesses().get(classpathResources.length - 1).getProcessDefinitionKey());
  }


  public static String deployProcess(ZeebeClient client, BpmnModelInstance processModel, String resourceName) {
    DeployProcessCommandStep1 deployProcessCommandStep1 = client.newDeployCommand()
      .addProcessModel(processModel, resourceName);
    final DeploymentEvent deploymentEvent =
      ((DeployProcessCommandStep1.DeployProcessCommandBuilderStep2)deployProcessCommandStep1)
        .send()
        .join();
    logger.debug("Deployment of resource [{}] was performed", resourceName);
    return String.valueOf(deploymentEvent.getProcesses().get(0).getProcessDefinitionKey());
  }

  public static ZeebeFuture<ProcessInstanceEvent> startProcessInstanceAsync(ZeebeClient client, String bpmnProcessId, String payload) {
    final CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3 createProcessInstanceCommandStep3 = client
      .newCreateInstanceCommand().bpmnProcessId(bpmnProcessId).latestVersion();
    if (payload != null) {
      createProcessInstanceCommandStep3.variables(payload);
    }
    return createProcessInstanceCommandStep3.send();
  }
  public static long startProcessInstance(ZeebeClient client, String bpmnProcessId, String payload) {
    final CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3 createProcessInstanceCommandStep3 = client
      .newCreateInstanceCommand().bpmnProcessId(bpmnProcessId).latestVersion();
    if (payload != null) {
      createProcessInstanceCommandStep3.variables(payload);
    }
    ProcessInstanceEvent processInstanceEvent = createProcessInstanceCommandStep3.send().join();
    logger.debug("Process instance created for process [{}]", bpmnProcessId);
    return processInstanceEvent.getProcessInstanceKey();
  }

  public static void completeTask(ZeebeClient client, String jobType, String workerName, String payload, int count) {
    final int[] countCompleted = { 0 };
    JobWorker jobWorker = client.newWorker()
        .jobType(jobType)
        .handler((jobClient, job) -> {
          try {
            if (countCompleted[0] < count) {
              CompleteJobCommandStep1 completeJobCommandStep1 = jobClient
                  .newCompleteCommand(job.getKey());
              if (payload != null) {
                completeJobCommandStep1 = completeJobCommandStep1.variables(payload);
              }
              completeJobCommandStep1.send().join();
              logger.debug("Task completed jobKey [{}]", job.getKey());
              countCompleted[0]++;
              if (countCompleted[0] % 1000 == 0) {
                logger.info("{} jobs completed ", countCompleted[0]);
              }
            }
          } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
            throw ex;
          }
        })
      .name(workerName)
      .timeout(Duration.ofSeconds(2))
      .open();
    //wait till all requested tasks are completed
    while(countCompleted[0] < count) {
      sleepFor(1000);
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
          client.newFailCommand(activatedJob.getKey()).retries(0).errorMessage(error).send().join();
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
      sleepFor(200);
    }
    jobWorker.close();
  }

}
