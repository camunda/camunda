package org.camunda.operate.zeebe;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.camunda.operate.es.reader.ZeebeMetadataReader;
import org.camunda.operate.es.writer.EntityStorage;
import org.camunda.operate.property.OperateProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.subscription.ManagementSubscriptionBuilderStep1;
import io.zeebe.client.api.subscription.TopicSubscription;
import io.zeebe.client.api.subscription.TopicSubscriptionBuilderStep1;
import io.zeebe.client.api.subscription.WorkflowInstanceEventHandler;
import io.zeebe.protocol.Protocol;

/**
 * @author Svetlana Dorokhova.
 */
@Component
@Configuration
@Profile("zeebe")
@DependsOn({"entityStorage"})
public class ZeebeConnector {

  private Logger logger = LoggerFactory.getLogger(ZeebeConnector.class);

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private WorkflowInstanceEventHandler workflowInstanceEventHandler;

  @Autowired
  private IncidentEventTransformer incidentEventTransformer;

  @Autowired
  private DeploymentEventTransformer deploymentEventTransformer;

  @Autowired
  private EntityStorage entityStorage;

  @Autowired
  private ZeebeMetadataReader zeebeMetadataReader;

  private Map<String, TopicSubscription> topicSubscriptions = new HashMap<>();

  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

  @Bean(destroyMethod = "close")
  public ZeebeClient zeebeClient() {

    final String brokerContactPoint = operateProperties.getZeebe().getBrokerContactPoint();

    ZeebeClient zeebeClient = ZeebeClient
      .newClientBuilder()
      .brokerContactPoint(brokerContactPoint)
      .build();

    return zeebeClient;
  }

  @PostConstruct
  public void startCheckingSubscriptions() {

    for (String topic : operateProperties.getZeebe().getTopics()) {
      checkAndCreateTopicSubscriptions(topic);
    }

    checkAndCreateTopicSubscriptions(Protocol.SYSTEM_TOPIC);

  }

  public void checkAndCreateTopicSubscriptions(String topic) {
    try {
      if (topicSubscriptions.get(topic) == null || !topicSubscriptions.get(topic).isOpen()) {
        topicSubscriptions.put(topic, createTopicSubscription(topic, zeebeMetadataReader.getPositionPerPartitionMap()));
      }
      logger.info("Subscriptions for topic [{}] was created", topic);
    } catch (Exception ex) {
      logger.error("Could not open topic subscription to Zeebe. Retrying...", ex);
      scheduleSubscriptionRetry(topic);
    }
  }

  private void scheduleSubscriptionRetry(String topic) {
    scheduler.schedule(() -> {
      checkAndCreateTopicSubscriptions(topic);
    }, 2, TimeUnit.SECONDS);
  }

  public void removeTopicSubscription(String topicName) {
    topicSubscriptions.remove(topicName);
  }

  private TopicSubscription createTopicSubscription(String topicName, Map<Integer, Long> positionPerPartitionMap) {

    if (topicName.equals(Protocol.SYSTEM_TOPIC)) {
      return createWorkflowSubscription(positionPerPartitionMap.get(Protocol.SYSTEM_PARTITION));
    } else {

      entityStorage.addQueueForTopic(topicName);
      TopicSubscriptionBuilderStep1.TopicSubscriptionBuilderStep3 topicSubscriptionBuilder =  zeebeClient()
        .topicClient(topicName)
        .newSubscription()
        .name(operateProperties.getZeebe().getWorker())
        .workflowInstanceEventHandler(workflowInstanceEventHandler)
        .incidentEventHandler(incidentEventTransformer)
        .startAtHeadOfTopic()
        .forcedStart();

      for (Map.Entry<Integer, Long> positionPerPartition: positionPerPartitionMap.entrySet()) {
        //we know the maximum position, that was process correctly, we subscribe from the next (+1)
        topicSubscriptionBuilder = topicSubscriptionBuilder.startAtPosition(positionPerPartition.getKey(), positionPerPartition.getValue() + 1);
      }

      return topicSubscriptionBuilder.open();
    }
  }

  private TopicSubscription createWorkflowSubscription(Long position) {
    ManagementSubscriptionBuilderStep1.ManagementSubscriptionBuilderStep3 managementSubscriptionBuilder =  zeebeClient()
      .newManagementSubscription()
      .name(operateProperties.getZeebe().getWorker())
      .deploymentEventHandler(deploymentEventTransformer)
      .startAtHeadOfTopic()
      .forcedStart();
    if (position != null) {
      //we know the maximum position, that was process correctly, we subscribe from the next (+1)
      managementSubscriptionBuilder = managementSubscriptionBuilder.startAtPosition(position + 1);
    }
    return managementSubscriptionBuilder.open();
  }

  public Map<String, TopicSubscription> getTopicSubscriptions() {
    return topicSubscriptions;
  }

  @PreDestroy
  public void closeSubscriptions() {
    for (TopicSubscription topicSubscription: topicSubscriptions.values()) {
      if (topicSubscription != null) {
        topicSubscription.close();
      }
    }
  }

}
