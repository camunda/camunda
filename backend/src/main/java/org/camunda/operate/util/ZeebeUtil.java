package org.camunda.operate.util;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.camunda.operate.zeebe.JobEventTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.clients.JobClient;
import io.zeebe.client.api.clients.TopicClient;
import io.zeebe.client.api.commands.DeployWorkflowCommandStep1;
import io.zeebe.client.api.events.DeploymentEvent;
import io.zeebe.client.api.events.JobEvent;
import io.zeebe.client.api.events.TopicEvent;
import io.zeebe.client.api.events.WorkflowInstanceEvent;
import io.zeebe.client.api.events.WorkflowInstanceState;
import io.zeebe.client.api.subscription.JobHandler;
import io.zeebe.client.api.subscription.JobWorker;
import io.zeebe.client.api.subscription.TopicSubscription;
import io.zeebe.client.impl.data.ZeebeObjectMapperImpl;
import io.zeebe.client.impl.event.JobEventImpl;
import io.zeebe.client.impl.event.WorkflowInstanceEventImpl;
import io.zeebe.model.bpmn.instance.WorkflowDefinition;

@Component
public class ZeebeUtil {

  private static final Logger logger = LoggerFactory.getLogger(ZeebeUtil.class);

  public static final Logger ALL_EVENTS_LOGGER = LoggerFactory.getLogger("org.camunda.operate.ALL_EVENTS");

  @Autowired
  private ZeebeClient client;

  /**
   * Creates the topic synchronously.
   * @param topicName
   */
  public void createTopic(String topicName) {
    final TopicEvent event = client.newCreateTopicCommand()
      .name(topicName)
      .partitions(1)
      .replicationFactor(1)
      .send().join();
    logger.debug("Topic {} created: {}", topicName, event.getState());
  }

  /**
   * Deploys the process synchronously.
   * @param topic
   * @param classpathResources
   * @return workflow id
   */
  public String deployWorkflowToTheTopic(String topic, String... classpathResources) {
    if (classpathResources.length == 0) {
      return null;
    }
    DeployWorkflowCommandStep1 deployWorkflowCommandStep1 = client.topicClient(topic).workflowClient().newDeployCommand();
    for (String classpathResource: classpathResources) {
      deployWorkflowCommandStep1 = deployWorkflowCommandStep1.addResourceFromClasspath(classpathResource);
    }
    final DeploymentEvent deploymentEvent =
      ((DeployWorkflowCommandStep1.DeployWorkflowCommandBuilderStep2)deployWorkflowCommandStep1)
        .send()
        .join();
    logger.debug("Deployment of resource [{}] was performed", classpathResources);
    return String.valueOf(deploymentEvent.getDeployedWorkflows().get(0).getWorkflowKey());
  }

  /**
   * Deploys the process synchronously.
   * @param topic
   * @param workflowModel
   * @return workflow id
   */
  public String deployWorkflowToTheTopic(String topic, WorkflowDefinition workflowModel, String resourceName) {
    DeployWorkflowCommandStep1 deployWorkflowCommandStep1 = client.topicClient(topic).workflowClient().newDeployCommand()
      .addWorkflowModel(workflowModel, resourceName);
    final DeploymentEvent deploymentEvent =
      ((DeployWorkflowCommandStep1.DeployWorkflowCommandBuilderStep2)deployWorkflowCommandStep1)
        .send()
        .join();
    logger.debug("Deployment of resource [{}] was performed", resourceName);
    return String.valueOf(deploymentEvent.getDeployedWorkflows().get(0).getWorkflowKey());
  }

  /**
   *
   * @param topic
   * @param bpmnProcessId
   * @param payload
   * @return workflow instance id
   */
  public String startWorkflowInstance(String topic, String bpmnProcessId, String payload) {
    final WorkflowInstanceEvent workflowInstanceEvent =
      client.topicClient(topic).workflowClient()
      .newCreateInstanceCommand().bpmnProcessId(bpmnProcessId)
      .latestVersionForce()
      .payload(payload)
      .send().join();
    logger.debug("Workflow instance created for workflow [{}]", bpmnProcessId);
    return IdUtil.createId(workflowInstanceEvent.getKey(), workflowInstanceEvent.getMetadata().getPartitionId());
  }

  public void cancelWorkflowInstance(String topic, String workflowInstanceId, String workflowKey) {

    long workflowInstanceKey = IdUtil.extractKey(workflowInstanceId);
    int partitionId = IdUtil.extractPartitionId(workflowInstanceId);

    WorkflowInstanceEventImpl workflowInstanceEvent = new WorkflowInstanceEventImpl(new ZeebeObjectMapperImpl());
    workflowInstanceEvent.setKey(workflowInstanceKey);
    workflowInstanceEvent.setVersion(1);
    workflowInstanceEvent.setWorkflowKey(Long.valueOf(workflowKey));
    workflowInstanceEvent.setWorkflowInstanceKey(workflowInstanceKey);
    workflowInstanceEvent.setTopicName(topic);
    workflowInstanceEvent.setPartitionId(partitionId);
    client.topicClient(topic).workflowClient().newCancelInstanceCommand(workflowInstanceEvent).send().join();

  }

  public JobWorker completeTask(String topicName, String jobType, String workerName, String payload) {
    return client.topicClient(topicName).jobClient().newWorker()
      .jobType(jobType)
      .handler((jobClient, job) -> {
        //incidents for outputMapping for taskA
        if (payload == null) {
          jobClient.newCompleteCommand(job).send().join();
        } else {
          jobClient.newCompleteCommand(job).payload(payload).send().join();
        }
      })
      .name(workerName)
      .timeout(Duration.ofSeconds(2))
      .open();
  }

  public JobWorker failTask(String topicName, String jobType, String workerName, int numberOfFailures) {
    return client.topicClient(topicName).jobClient().newWorker()
      .jobType(jobType)
      .handler( new FailJobHandler(numberOfFailures))
      .name(workerName)
      .timeout(Duration.ofSeconds(2))
      .open();
  }

  private static class FailJobHandler implements JobHandler {

    private int numberOfFailures;

    private int failuresCount = 0;

    public FailJobHandler(int numberOfFailures) {
      this.numberOfFailures = numberOfFailures;
    }

    @Override
    public void handle(JobClient client, JobEvent job) {
      if (failuresCount < numberOfFailures) {
        client.newFailCommand(job).retries(job.getRetries() - 1).send().join();
        failuresCount++;
      }
    }
  }

  public TopicSubscription resolveIncident(String topicName, String subscriptionName, String workflowId, String payload) {

    return client.topicClient(topicName).newSubscription().name(subscriptionName).incidentEventHandler(incidentEvent -> {
      JobEventImpl jobEvent = new JobEventImpl(new ZeebeObjectMapperImpl());
      if (incidentEvent.getJobKey() != null) {
        jobEvent.setKey(incidentEvent.getJobKey());
      }
      jobEvent.setTopicName(topicName);
      jobEvent.setPartitionId(incidentEvent.getMetadata().getPartitionId());
      jobEvent.setType(incidentEvent.getActivityId());
      jobEvent.setPayload(payload);
      Map<String, Object> headers = new HashMap<>();
      headers.put(JobEventTransformer.WORKFLOW_INSTANCE_KEY_HEADER, incidentEvent.getWorkflowInstanceKey());
      if (workflowId != null) {
        headers.put(JobEventTransformer.WORKFLOW_KEY_HEADER, Long.valueOf(workflowId));
      }
      headers.put(JobEventTransformer.BPMN_PROCESS_ID_HEADER, incidentEvent.getBpmnProcessId());
      headers.put(JobEventTransformer.ACTIVITY_INSTANCE_KEY_HEADER, incidentEvent.getActivityInstanceKey());
      headers.put(JobEventTransformer.ACTIVITY_ID_HEADER, incidentEvent.getActivityId());
      jobEvent.setHeaders(headers);
      client.topicClient(topicName).jobClient().newUpdateRetriesCommand(jobEvent).retries(3).send().join();
    }).startAtHeadOfTopic().open();
  }

  public void updatePayload(String topicName, Long key, String workflowInstanceId, String newPayload, String bpmnProcessId, String workflowId) {

    long workflowInstanceKey = IdUtil.extractKey(workflowInstanceId);
    int partitionId = IdUtil.extractPartitionId(workflowInstanceId);

    WorkflowInstanceEventImpl workflowInstanceEvent = new WorkflowInstanceEventImpl(new ZeebeObjectMapperImpl());
    workflowInstanceEvent.setKey(key);
    workflowInstanceEvent.setBpmnProcessId(bpmnProcessId);
    workflowInstanceEvent.setVersion(1);
    workflowInstanceEvent.setWorkflowKey(Long.valueOf(workflowId));
    workflowInstanceEvent.setWorkflowInstanceKey(workflowInstanceKey);
    workflowInstanceEvent.setPayload(newPayload);
    workflowInstanceEvent.setTopicName(topicName);
    workflowInstanceEvent.setPartitionId(partitionId);
    client.topicClient(topicName).workflowClient().newUpdatePayloadCommand(workflowInstanceEvent).payload(newPayload).send().join();
  }

  public void setClient(ZeebeClient client) {
    this.client = client;
  }
}
