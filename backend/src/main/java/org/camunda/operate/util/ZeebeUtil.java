package org.camunda.operate.util;

import java.time.Duration;
import java.util.UUID;
import org.camunda.operate.property.OperateProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.clients.TopicClient;
import io.zeebe.client.api.commands.DeployWorkflowCommandStep1;
import io.zeebe.client.api.events.DeploymentEvent;
import io.zeebe.client.api.events.TopicEvent;
import io.zeebe.client.api.events.WorkflowInstanceEvent;
import io.zeebe.client.api.events.WorkflowInstanceState;
import io.zeebe.client.api.subscription.JobWorker;
import io.zeebe.client.api.subscription.TopicSubscription;

@Component
public class ZeebeUtil {

  private Logger logger = LoggerFactory.getLogger(ZeebeUtil.class);

  @Autowired
  private ZeebeClient client;

  @Autowired
  private OperateProperties operateProperties;

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
    logger.debug("Topic created: " + event.getState());
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
      .latestVersion()
      .payload(payload)
      .send().join();
    logger.debug("Workflow instance created for workflow [{}]", bpmnProcessId);
    return String.valueOf(workflowInstanceEvent.getKey());
  }

  public TopicSubscription cancelWorkflowInstance(String topic, String workflowInstanceId) {
    final TopicClient topicClient = client.topicClient(topic);
    return topicClient.newSubscription().name(UUID.randomUUID().toString().substring(10)).workflowInstanceEventHandler(
      event -> {
        if (workflowInstanceId.equals(String.valueOf(event.getWorkflowInstanceKey())) &&
          ! event.getState().equals(WorkflowInstanceState.CANCELED) && ! event.getState().equals(WorkflowInstanceState.COMPLETED)) {
          topicClient.workflowClient().newCancelInstanceCommand(event).send();
        }
      }
    ).startAtHeadOfTopic().forcedStart().open();
  }

  public JobWorker completeTaskWithIncident(String topicName, String jobType, String workerName) {
    return client.topicClient(topicName).jobClient().newWorker()
      .jobType(jobType)
      .handler((jobClient, job) -> {
        //incidents for outputMapping for taskA
        jobClient.newCompleteCommand(job).payload((String)null).send().join();
      })
      .name(workerName)
      .timeout(Duration.ofSeconds(2))
      .open();
  }
}
