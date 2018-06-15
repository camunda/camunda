package org.camunda.operate.zeebe;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import org.camunda.operate.entities.OperateEntity;
import org.camunda.operate.entities.WorkflowInstanceEntity;
import org.camunda.operate.entities.WorkflowInstanceState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import io.zeebe.client.api.events.WorkflowInstanceEvent;
import io.zeebe.client.api.subscription.WorkflowInstanceEventHandler;

/**
 * @author Svetlana Dorokhova.
 */
@Component
@Profile("zeebe")
public class WorkflowEventTransformer implements WorkflowInstanceEventHandler {

  private Logger logger = LoggerFactory.getLogger(WorkflowEventTransformer.class);

  private static final Set<io.zeebe.client.api.events.WorkflowInstanceState> STATES = new HashSet<>();
  private static final Set<io.zeebe.client.api.events.WorkflowInstanceState> END_STATES = new HashSet<>();

  static {
    END_STATES.add(io.zeebe.client.api.events.WorkflowInstanceState.COMPLETED);
    END_STATES.add(io.zeebe.client.api.events.WorkflowInstanceState.CANCELED);

    STATES.add(io.zeebe.client.api.events.WorkflowInstanceState.CREATED);
    STATES.addAll(END_STATES);
  }

  @Autowired
  private BlockingQueue<OperateEntity> operateEntities;

  @Override
  public void onWorkflowInstanceEvent(WorkflowInstanceEvent event) throws Exception {
    if (STATES.contains(event.getState())) {

      logger.debug(event.toJson());

      WorkflowInstanceEntity entity = new WorkflowInstanceEntity();
      entity.setId(String.valueOf(event.getWorkflowInstanceKey()));
      entity.setWorkflowDefinitionId(String.valueOf(event.getWorkflowKey()));
      entity.setBusinessKey(event.getBpmnProcessId());
      if (END_STATES.contains(event.getState())) {
        entity.setEndDate(OffsetDateTime.ofInstant(event.getMetadata().getTimestamp(), ZoneOffset.UTC));
        entity.setState(WorkflowInstanceState.COMPLETED);
      } else {
        entity.setState(WorkflowInstanceState.ACTIVE);
        entity.setStartDate(OffsetDateTime.ofInstant(event.getMetadata().getTimestamp(), ZoneOffset.UTC));
      }

      //TODO will wait till capacity available, can throw InterruptedException
      operateEntities.put(entity);
    }
  }

}
