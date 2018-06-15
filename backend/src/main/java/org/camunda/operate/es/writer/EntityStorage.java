package org.camunda.operate.es.writer;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.entities.OperateEntity;
import org.camunda.operate.entities.WorkflowInstanceEntity;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * @author Svetlana Dorokhova.
 */
@Configuration
@Profile({"zeebe", "elasticsearch"})
public class EntityStorage {

  @Bean
  public BlockingQueue<OperateEntity> operateEntitiesForPersist() {
    return new LinkedBlockingQueue<>(100);      //TODO size must correspond to TopicSubscriptionBufferSize
  }

}
