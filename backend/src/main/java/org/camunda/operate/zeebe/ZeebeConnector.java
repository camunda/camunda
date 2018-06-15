package org.camunda.operate.zeebe;

import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import org.camunda.operate.es.ElasticsearchConnector;
import org.camunda.operate.property.OperateProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
public class ZeebeConnector {

  private Logger logger = LoggerFactory.getLogger(ElasticsearchConnector.class);

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private WorkflowInstanceEventHandler workflowInstanceEventHandler;

  @Autowired
  private IncidentEventTransformer incidentEventTransformer;

  @Bean(destroyMethod = "close")
  public ZeebeClient zeebeClient() {

    final String brokerContactPoint = operateProperties.getZeebe().getBrokerContactPoint();

    ZeebeClient zeebeClient = ZeebeClient
      .newClientBuilder()
      .brokerContactPoint(brokerContactPoint)
      .build();

    return zeebeClient;
  }

  @Bean
  public List<TopicSubscription> getTopicSubscriptions() {
    List<TopicSubscription> topicSubscriptions = new ArrayList<>();
    for (String topic: operateProperties.getZeebe().getTopics()) {

      try {
        topicSubscriptions.add(zeebeClient()
          .topicClient(topic)
          .newSubscription()
          .name(operateProperties.getZeebe().getWorker())
          .workflowInstanceEventHandler(workflowInstanceEventHandler)
          .incidentEventHandler(incidentEventTransformer)
          .startAtHeadOfTopic()   //TODO
          .forcedStart()
          .open());
      } catch (Exception e) {
        logger.error("Could not open topic subscription to Zeebe. Please check if the broker is up running!", e);
        //TODO retry etc.
      }

    }
    return topicSubscriptions;
  }

  @PreDestroy
  public void closeSubscriptions() {
    for (TopicSubscription topicSubscription: getTopicSubscriptions()) {
      topicSubscription.close();
    }
  }

}
