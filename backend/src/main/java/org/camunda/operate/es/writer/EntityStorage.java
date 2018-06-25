package org.camunda.operate.es.writer;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.camunda.operate.entities.OperateEntity;
import org.camunda.operate.property.OperateProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;


@Component
@Configuration
public class EntityStorage {

  private Logger logger = LoggerFactory.getLogger(EntityStorage.class);

  @Autowired
  private OperateProperties operateProperties;

  private Map<String, BlockingQueue<OperateEntity>> operateEntityMap;

  @PostConstruct
  public void initMap() {
    operateEntityMap = new HashMap<>(operateProperties.getZeebe().getTopics().size());
  }

  //TODO this method is not thread-safe, but in theory is must not be called from concurrent threads :)
  public void addQueueForTopic(String topicName) {
    if (operateEntityMap.get(topicName) != null) {
      logger.warn("Internal entity queue for topic [{}] already exist", topicName);
    } else {
      operateEntityMap.put(topicName, new LinkedBlockingQueue<>(100));    //TODO size must correspond to TopicSubscriptionBufferSize
    }
  }

  public BlockingQueue<OperateEntity> getOperateEntititesQueue(String topicName) {
    return operateEntityMap.get(topicName);
  }

}
