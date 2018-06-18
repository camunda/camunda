package org.camunda.operate.zeebe;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.HashMap;
import java.util.Map;
import org.camunda.operate.es.ElasticsearchConnector;
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
import io.zeebe.client.api.subscription.TopicSubscription;
import io.zeebe.client.api.subscription.WorkflowInstanceEventHandler;

/**
 * @author Svetlana Dorokhova.
 */
@Component
@Configuration
@Profile("zeebe")
@DependsOn("entityStorage")
public class ZeebeConnector {

  private Logger logger = LoggerFactory.getLogger(ElasticsearchConnector.class);

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private WorkflowInstanceEventHandler workflowInstanceEventHandler;

  @Autowired
  private IncidentEventTransformer incidentEventTransformer;

  @Autowired
  private EntityStorage entityStorage;

  private Map<String, TopicSubscription> topicSubscriptions = new HashMap<>();

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

    checkAndCreateTopicSubscriptions();
    logger.info("Subscriptions for demo data generation was canceled");

  }

  public void checkAndCreateTopicSubscriptions() {
    for (String topic: operateProperties.getZeebe().getTopics()) {
      if (topicSubscriptions.get(topic) == null || !topicSubscriptions.get(topic).isOpen()) {
        topicSubscriptions.put(topic, createTopicSubscription(topic));
      }
    }
  }

  public void removeTopicSubscription(String topicName) {
    topicSubscriptions.remove(topicName);
  }

  private TopicSubscription createTopicSubscription(String topicName) {
    try {
      entityStorage.addQueueForTopic(topicName);
      return zeebeClient() //TODO ???
        .topicClient(topicName)
        .newSubscription()
        .name(operateProperties.getZeebe().getWorker())
        .workflowInstanceEventHandler(workflowInstanceEventHandler)
        .incidentEventHandler(incidentEventTransformer)
        .startAtHeadOfTopic()   //TODO
        .forcedStart()          //TODO
        .open();
    } catch (Exception e) {
      logger.error("Could not open topic subscription to Zeebe. Please check if the broker is up running!", e);
      //TODO retry etc.
      return null;
    }
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
