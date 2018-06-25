package org.camunda.operate.zeebe;

import java.util.HashSet;
import java.util.Set;
import org.camunda.operate.entities.ActivityInstanceEntity;
import org.camunda.operate.entities.ActivityState;
import org.camunda.operate.entities.WorkflowInstanceEntity;
import org.camunda.operate.entities.WorkflowInstanceState;
import org.camunda.operate.es.writer.EntityStorage;
import org.camunda.operate.util.DateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import io.zeebe.client.api.events.WorkflowInstanceEvent;
import io.zeebe.client.api.subscription.WorkflowInstanceEventHandler;


@Component
public class WorkflowInstanceEventTransformer extends AbstractEventTransformer implements WorkflowInstanceEventHandler {

  private Logger logger = LoggerFactory.getLogger(WorkflowInstanceEventTransformer.class);

  private static final Set<io.zeebe.client.api.events.WorkflowInstanceState> WORKFLOW_INSTANCE_STATES = new HashSet<>();
  private static final Set<io.zeebe.client.api.events.WorkflowInstanceState> ACTIVITY_INSTANCE_STATES = new HashSet<>();
  private static final Set<io.zeebe.client.api.events.WorkflowInstanceState> WORKFLOW_INSTANCE_END_STATES = new HashSet<>();
  private static final Set<io.zeebe.client.api.events.WorkflowInstanceState> ACTIVITY_INSTANCE_END_STATES = new HashSet<>();
  private static final Set<io.zeebe.client.api.events.WorkflowInstanceState> ACTIVITY_INSTANCE_START_END_STATES = new HashSet<>();

  static {
    WORKFLOW_INSTANCE_END_STATES.add(io.zeebe.client.api.events.WorkflowInstanceState.COMPLETED);
    WORKFLOW_INSTANCE_END_STATES.add(io.zeebe.client.api.events.WorkflowInstanceState.CANCELED);

    ACTIVITY_INSTANCE_END_STATES.add(io.zeebe.client.api.events.WorkflowInstanceState.ACTIVITY_COMPLETED);
    ACTIVITY_INSTANCE_END_STATES.add(io.zeebe.client.api.events.WorkflowInstanceState.ACTIVITY_TERMINATED);

    ACTIVITY_INSTANCE_START_END_STATES.add(io.zeebe.client.api.events.WorkflowInstanceState.START_EVENT_OCCURRED);
    ACTIVITY_INSTANCE_START_END_STATES.add(io.zeebe.client.api.events.WorkflowInstanceState.END_EVENT_OCCURRED);
    ACTIVITY_INSTANCE_START_END_STATES.add(io.zeebe.client.api.events.WorkflowInstanceState.GATEWAY_ACTIVATED);

    WORKFLOW_INSTANCE_STATES.add(io.zeebe.client.api.events.WorkflowInstanceState.CREATED);
    WORKFLOW_INSTANCE_STATES.addAll(WORKFLOW_INSTANCE_END_STATES);

    ACTIVITY_INSTANCE_STATES.add(io.zeebe.client.api.events.WorkflowInstanceState.ACTIVITY_READY);
    ACTIVITY_INSTANCE_STATES.addAll(ACTIVITY_INSTANCE_END_STATES);
    ACTIVITY_INSTANCE_STATES.addAll(ACTIVITY_INSTANCE_START_END_STATES);
  }

  @Autowired
  private EntityStorage entityStorage;

  @Override
  public void onWorkflowInstanceEvent(WorkflowInstanceEvent event) throws Exception {
    if (WORKFLOW_INSTANCE_STATES.contains(event.getState())) {

      convertWorkflowInstanceEvent(event);

    } else if (ACTIVITY_INSTANCE_STATES.contains(event.getState())) {

      convertActivityInstanceEvent(event);

    }
  }

  private void convertActivityInstanceEvent(WorkflowInstanceEvent event) throws InterruptedException {
    logger.debug(event.toJson());

    ActivityInstanceEntity activityInstanceEntity = new ActivityInstanceEntity();
    activityInstanceEntity.setId(String.valueOf(event.getKey()));
    activityInstanceEntity.setActivityId(event.getActivityId());
    activityInstanceEntity.setWorkflowInstanceId(String.valueOf(event.getWorkflowInstanceKey()));
    if (ACTIVITY_INSTANCE_END_STATES.contains(event.getState())) {
      activityInstanceEntity.setEndDate(DateUtil.toOffsetDateTime(event.getMetadata().getTimestamp()));
      if (event.getState().equals(io.zeebe.client.api.events.WorkflowInstanceState.ACTIVITY_TERMINATED)) {
        activityInstanceEntity.setState(ActivityState.TERMINATED);
      } else {
        activityInstanceEntity.setState(ActivityState.COMPLETED);
      }
    } else if (ACTIVITY_INSTANCE_START_END_STATES.contains(event.getState())) {
      activityInstanceEntity.setStartDate(DateUtil.toOffsetDateTime(event.getMetadata().getTimestamp()));
      activityInstanceEntity.setEndDate(DateUtil.toOffsetDateTime(event.getMetadata().getTimestamp()));
      activityInstanceEntity.setState(ActivityState.COMPLETED);
    } else {
      activityInstanceEntity.setStartDate(DateUtil.toOffsetDateTime(event.getMetadata().getTimestamp()));
      activityInstanceEntity.setState(ActivityState.ACTIVE);
    }

    updateMetadataFields(activityInstanceEntity, event);

    //TODO will wait till capacity available, can throw InterruptedException
    entityStorage.getOperateEntititesQueue(event.getMetadata().getTopicName()).put(activityInstanceEntity);
  }

  private void convertWorkflowInstanceEvent(WorkflowInstanceEvent event) throws InterruptedException {
    logger.debug(event.toJson());

    WorkflowInstanceEntity entity = new WorkflowInstanceEntity();
    entity.setId(String.valueOf(event.getWorkflowInstanceKey()));
    entity.setWorkflowId(String.valueOf(event.getWorkflowKey()));
    entity.setBusinessKey(event.getBpmnProcessId());
    if (WORKFLOW_INSTANCE_END_STATES.contains(event.getState())) {
      entity.setEndDate(DateUtil.toOffsetDateTime(event.getMetadata().getTimestamp()));
      entity.setState(WorkflowInstanceState.COMPLETED);
    } else {
      entity.setState(WorkflowInstanceState.ACTIVE);
      entity.setStartDate(DateUtil.toOffsetDateTime(event.getMetadata().getTimestamp()));
    }

    updateMetadataFields(entity, event);

    //TODO will wait till capacity available, can throw InterruptedException
    entityStorage.getOperateEntititesQueue(event.getMetadata().getTopicName()).put(entity);
  }

}
