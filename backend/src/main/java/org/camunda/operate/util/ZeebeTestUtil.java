package org.camunda.operate.util;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.clients.JobClient;
import io.zeebe.client.api.commands.CreateWorkflowInstanceCommandStep1;
import io.zeebe.client.api.commands.DeployWorkflowCommandStep1;
import io.zeebe.client.api.commands.FailJobCommandStep1;
import io.zeebe.client.api.commands.FinalCommandStep;
import io.zeebe.client.api.events.DeploymentEvent;
import io.zeebe.client.api.events.WorkflowInstanceEvent;
import io.zeebe.client.api.response.ActivatedJob;
import io.zeebe.client.api.subscription.JobHandler;
import io.zeebe.client.api.subscription.JobWorker;
import io.zeebe.client.cmd.ClientException;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.Protocol;

public abstract class ZeebeTestUtil {

  private static final Logger logger = LoggerFactory.getLogger(ZeebeTestUtil.class);

  public static final Logger ALL_EVENTS_LOGGER = LoggerFactory.getLogger("org.camunda.operate.ALL_EVENTS");

  /**
   * Deploys the process synchronously.
   * @param client
   * @param classpathResources
   * @return workflow id
   */
  public static String deployWorkflow(ZeebeClient client, String... classpathResources) {
    if (classpathResources.length == 0) {
      return null;
    }
    DeployWorkflowCommandStep1 deployWorkflowCommandStep1 = client.workflowClient().newDeployCommand();
    for (String classpathResource: classpathResources) {
      deployWorkflowCommandStep1 = deployWorkflowCommandStep1.addResourceFromClasspath(classpathResource);
    }
    final DeploymentEvent deploymentEvent =
      ((DeployWorkflowCommandStep1.DeployWorkflowCommandBuilderStep2)deployWorkflowCommandStep1)
        .send()
        .join();
    logger.debug("Deployment of resource [{}] was performed", classpathResources);
    return String.valueOf(deploymentEvent.getWorkflows().get(classpathResources.length - 1).getWorkflowKey());
  }

  /**
   * Deploys the process synchronously.
   * @param client
   * @param workflowModel
   * @return workflow id
   */
  public static String deployWorkflow(ZeebeClient client, BpmnModelInstance workflowModel, String resourceName) {
    DeployWorkflowCommandStep1 deployWorkflowCommandStep1 = client.workflowClient().newDeployCommand()
      .addWorkflowModel(workflowModel, resourceName);
    final DeploymentEvent deploymentEvent =
      ((DeployWorkflowCommandStep1.DeployWorkflowCommandBuilderStep2)deployWorkflowCommandStep1)
        .send()
        .join();
    logger.debug("Deployment of resource [{}] was performed", resourceName);
    return String.valueOf(deploymentEvent.getWorkflows().get(0).getWorkflowKey());
  }

  /**
   *
   * @param client
   * @param bpmnProcessId
   * @param payload
   * @return workflow instance id
   */
  public static long startWorkflowInstance(ZeebeClient client, String bpmnProcessId, String payload) {
    final CreateWorkflowInstanceCommandStep1.CreateWorkflowInstanceCommandStep3 createWorkflowInstanceCommandStep3 = client.workflowClient()
      .newCreateInstanceCommand().bpmnProcessId(bpmnProcessId).latestVersion();
    if (payload != null) {
      createWorkflowInstanceCommandStep3.payload(payload);
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
    client.workflowClient().newCancelInstanceCommand(workflowInstanceKey).send().join();

  }

  public static JobWorker completeTask(ZeebeClient client, String jobType, String workerName, String payload) {
    return client.jobClient().newWorker()
      .jobType(jobType)
      .handler((jobClient, job) -> {
        if (payload == null) {
          jobClient.newCompleteCommand(job.getKey()).payload(job.getPayload()).send().join();
        } else {
          jobClient.newCompleteCommand(job.getKey()).payload(payload).send().join();
        }
        logger.debug("Complete task command was sent to Zeebe for jobKey [{}]", job.getKey());
      })
      .name(workerName)
      .timeout(Duration.ofSeconds(2))
      .open();
  }

  public static void completeTask(ZeebeClient client, String jobType, String workerName, String payload, int count) {
    final int[] countCompleted = { 0 };
    JobWorker jobWorker = client.jobClient().newWorker()
      .jobType(jobType)
      .handler((jobClient, job) -> {
        if (payload == null) {
          jobClient.newCompleteCommand(job.getKey()).payload(job.getPayload()).send().join();
        } else {
          jobClient.newCompleteCommand(job.getKey()).payload(payload).send().join();
        }
        logger.debug("Complete task command was sent to Zeebe for jobKey [{}]", job.getKey());
        countCompleted[0]++;
      })
      .name(workerName)
      .timeout(Duration.ofSeconds(2))
      .open();
    int attemptsCount = 0;
    while(countCompleted[0] < count && attemptsCount < 3) {
      try {
        Thread.sleep(200);
      } catch (InterruptedException e) {
      }
      attemptsCount++;
    }
    jobWorker.close();
  }

  /**
   * Returns jobKey.
   * @param client
   * @param jobType
   * @param workerName
   * @param numberOfFailures
   * @return
   */
  public static Long failTask(ZeebeClient client, String jobType, String workerName, int numberOfFailures, String errorMessage) {
    final FailJobHandler jobHandler = new FailJobHandler(numberOfFailures, errorMessage);
    JobWorker jobWorker = client.jobClient().newWorker()
      .jobType(jobType)
      .handler(jobHandler)
      .name(workerName)
      .timeout(Duration.ofSeconds(2))
      .open();
    //wait till job will fail 3 times
    while (jobHandler.failuresCount < jobHandler.numberOfFailures) {
      try {
        Thread.sleep(100L);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
    jobWorker.close();
    return jobHandler.getJobKey();
  }

  private static class FailJobHandler implements JobHandler {

    private int numberOfFailures;

    private int failuresCount = 0;

    private Long jobKey;

    private String errorMessage;

    public FailJobHandler(int numberOfFailures, String errorMessage) {
      this.numberOfFailures = numberOfFailures;
      this.errorMessage = errorMessage;
    }

    @Override
    public void handle(JobClient client, ActivatedJob job) {
      this.jobKey = job.getKey();
      if (failuresCount < numberOfFailures) {
        FinalCommandStep failCmd = client.newFailCommand(job.getKey()).retries(job.getRetries() - 1);
        if (errorMessage != null) {
          failCmd = ((FailJobCommandStep1.FailJobCommandStep2)failCmd).errorMessage(errorMessage);
        }
        failCmd.send().join();
        failuresCount++;
      }
    }

    public Long getJobKey() {
      return jobKey;
    }

    public void setJobKey(Long jobKey) {
      this.jobKey = jobKey;
    }
  }

  public static void resolveIncident(ZeebeClient client, long jobKey, long incidentKey) {
    client.jobClient().newUpdateRetriesCommand(jobKey).retries(3).send().join();
    client.workflowClient().newResolveIncidentCommand(incidentKey).send().join();
  }

//  public static void updatePayload(ZeebeClient client, Long key, String workflowInstanceId, String newPayload, String bpmnProcessId, String workflowId) {
//
//    long workflowInstanceKey = IdUtil.extractKey(workflowInstanceId);
//    int partitionId = IdUtil.extractPartitionId(workflowInstanceId);
//
//    WorkflowInstanceEventImpl workflowInstanceEvent = new WorkflowInstanceEventImpl(new ZeebeObjectMapperImpl());
//    workflowInstanceEvent.setKey(key);
//    workflowInstanceEvent.setBpmnProcessId(bpmnProcessId);
//    workflowInstanceEvent.setVersion(1);
//    workflowInstanceEvent.setWorkflowKey(Long.valueOf(workflowId));
//    workflowInstanceEvent.setWorkflowInstanceKey(workflowInstanceKey);
//    workflowInstanceEvent.setPayload(newPayload);
//    workflowInstanceEvent.setPartitionId(partitionId);
//    client.workflowClient().newUpdatePayloadCommand(workflowInstanceEvent).payload(newPayload).send().join();
//  }

}
