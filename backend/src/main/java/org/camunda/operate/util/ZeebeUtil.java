package org.camunda.operate.util;

import org.camunda.operate.property.OperateProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.events.DeploymentEvent;
import io.zeebe.client.api.events.TopicEvent;
import io.zeebe.client.api.events.WorkflowInstanceEvent;

/**
 * @author Svetlana Dorokhova.
 */
@Component
@Profile("zeebe")
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
   * @param classpathResource
   * @return workflow id
   */
  public String deployWorkflowToTheTopic(String topic, String classpathResource) {
    final DeploymentEvent deploymentEvent =
      client.topicClient(topic)
        .workflowClient()
        .newDeployCommand()
        .addResourceFromClasspath(classpathResource)
        .send()
        .join();
    logger.debug("Workflow [{}] was deployed", classpathResource);
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
}
