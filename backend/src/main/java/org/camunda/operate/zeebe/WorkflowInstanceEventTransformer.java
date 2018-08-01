package org.camunda.operate.zeebe;

import java.util.HashSet;
import java.util.Set;
import org.camunda.operate.entities.ActivityInstanceEntity;
import org.camunda.operate.entities.ActivityState;
import org.camunda.operate.entities.EventEntity;
import org.camunda.operate.entities.WorkflowInstanceEntity;
import org.camunda.operate.entities.WorkflowInstanceState;
import org.camunda.operate.es.writer.EntityStorage;
import org.camunda.operate.util.DateUtil;
import org.camunda.operate.util.ZeebeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import io.zeebe.client.api.events.WorkflowInstanceEvent;
import io.zeebe.client.api.record.Record;
import io.zeebe.client.api.record.RecordMetadata;
import io.zeebe.client.api.subscription.WorkflowInstanceEventHandler;
import static io.zeebe.client.api.events.WorkflowInstanceState.ACTIVITY_ACTIVATED;
import static io.zeebe.client.api.events.WorkflowInstanceState.ACTIVITY_COMPLETED;
import static io.zeebe.client.api.events.WorkflowInstanceState.ACTIVITY_COMPLETING;
import static io.zeebe.client.api.events.WorkflowInstanceState.ACTIVITY_READY;
import static io.zeebe.client.api.events.WorkflowInstanceState.ACTIVITY_TERMINATED;
import static io.zeebe.client.api.events.WorkflowInstanceState.CANCELED;
import static io.zeebe.client.api.events.WorkflowInstanceState.COMPLETED;
import static io.zeebe.client.api.events.WorkflowInstanceState.CREATED;
import static io.zeebe.client.api.events.WorkflowInstanceState.END_EVENT_OCCURRED;
import static io.zeebe.client.api.events.WorkflowInstanceState.GATEWAY_ACTIVATED;
import static io.zeebe.client.api.events.WorkflowInstanceState.SEQUENCE_FLOW_TAKEN;
import static io.zeebe.client.api.events.WorkflowInstanceState.START_EVENT_OCCURRED;


@Component
public class WorkflowInstanceEventTransformer extends AbstractEventTransformer implements WorkflowInstanceEventHandler {

  private static final Logger logger = LoggerFactory.getLogger(WorkflowInstanceEventTransformer.class);

  private static final Set<io.zeebe.client.api.events.WorkflowInstanceState> WORKFLOW_INSTANCE_STATES = new HashSet<>();
  private static final Set<io.zeebe.client.api.events.WorkflowInstanceState> ACTIVITY_INSTANCE_STATES = new HashSet<>();
  private static final Set<io.zeebe.client.api.events.WorkflowInstanceState> WORKFLOW_INSTANCE_END_STATES = new HashSet<>();
  private static final Set<io.zeebe.client.api.events.WorkflowInstanceState> ACTIVITY_INSTANCE_END_STATES = new HashSet<>();
  private static final Set<io.zeebe.client.api.events.WorkflowInstanceState> ACTIVITY_INSTANCE_START_END_STATES = new HashSet<>();

  static {
    WORKFLOW_INSTANCE_END_STATES.add(COMPLETED);
    WORKFLOW_INSTANCE_END_STATES.add(CANCELED);

    ACTIVITY_INSTANCE_END_STATES.add(ACTIVITY_COMPLETED);
    ACTIVITY_INSTANCE_END_STATES.add(ACTIVITY_TERMINATED);

    ACTIVITY_INSTANCE_START_END_STATES.add(START_EVENT_OCCURRED);
    ACTIVITY_INSTANCE_START_END_STATES.add(END_EVENT_OCCURRED);
    ACTIVITY_INSTANCE_START_END_STATES.add(GATEWAY_ACTIVATED);

    WORKFLOW_INSTANCE_STATES.add(CREATED);
    WORKFLOW_INSTANCE_STATES.addAll(WORKFLOW_INSTANCE_END_STATES);

    ACTIVITY_INSTANCE_STATES.add(ACTIVITY_READY);
    ACTIVITY_INSTANCE_STATES.add(ACTIVITY_ACTIVATED);
    ACTIVITY_INSTANCE_STATES.add(ACTIVITY_COMPLETING);
    ACTIVITY_INSTANCE_STATES.addAll(ACTIVITY_INSTANCE_END_STATES);
    ACTIVITY_INSTANCE_STATES.addAll(ACTIVITY_INSTANCE_START_END_STATES);
  }

  @Autowired
  private EntityStorage entityStorage;

  @Override
  public void onWorkflowInstanceEvent(WorkflowInstanceEvent event) throws Exception {

    ZeebeUtil.ALL_EVENTS_LOGGER.debug(event.toJson());

    convertEvent(event);

    if (WORKFLOW_INSTANCE_STATES.contains(event.getState())) {

      convertWorkflowInstanceEvent(event);

    } else if (ACTIVITY_INSTANCE_STATES.contains(event.getState())) {

      convertActivityInstanceEvent(event);

    }

  }

  private void convertEvent(WorkflowInstanceEvent event) throws InterruptedException {
    //we will store sequence flows separately, no need to store them in events
    if (!event.getState().equals(SEQUENCE_FLOW_TAKEN)) {

      EventEntity eventEntity = new EventEntity();

      loadEventGeneralData(event, eventEntity);

      eventEntity.setPayload(event.getPayload());
      eventEntity.setWorkflowId(String.valueOf(event.getWorkflowKey()));
      eventEntity.setWorkflowInstanceId(String.valueOf(event.getWorkflowInstanceKey()));
      eventEntity.setBpmnProcessId(event.getBpmnProcessId());

      if (event.getActivityId() != null) {
        eventEntity.setActivityId(event.getActivityId());
      }

      if (ACTIVITY_INSTANCE_STATES.contains(event.getState())) {
        eventEntity.setActivityInstanceId(String.valueOf(event.getKey()));
      }

      RecordMetadata metadata = event.getMetadata();
      String topicName = metadata.getTopicName();
      entityStorage.getOperateEntitiesQueue(topicName).put(eventEntity);
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
      if (event.getState().equals(ACTIVITY_TERMINATED)) {
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
    entityStorage.getOperateEntitiesQueue(event.getMetadata().getTopicName()).put(activityInstanceEntity);
  }

  private void updateMetadataFields(ActivityInstanceEntity operateEntity, Record zeebeRecord) {
    RecordMetadata metadata = zeebeRecord.getMetadata();
    operateEntity.setPosition(metadata.getPosition());
  }

  private void convertWorkflowInstanceEvent(WorkflowInstanceEvent event) throws InterruptedException {
    logger.debug(event.toJson());

    WorkflowInstanceEntity entity = new WorkflowInstanceEntity();
    entity.setId(String.valueOf(event.getWorkflowInstanceKey()));
    entity.setWorkflowId(String.valueOf(event.getWorkflowKey()));
    entity.setBusinessKey(event.getBpmnProcessId());
    if (WORKFLOW_INSTANCE_END_STATES.contains(event.getState())) {
      entity.setEndDate(DateUtil.toOffsetDateTime(event.getMetadata().getTimestamp()));
      if (event.getState().equals(CANCELED)) {
        entity.setState(WorkflowInstanceState.CANCELED);
      } else {
        entity.setState(WorkflowInstanceState.COMPLETED);
      }
    } else {
      entity.setState(WorkflowInstanceState.ACTIVE);
      entity.setStartDate(DateUtil.toOffsetDateTime(event.getMetadata().getTimestamp()));
    }

    updateMetadataFields(entity, event);

    // TODO will wait till capacity available, can throw InterruptedException
    entityStorage.getOperateEntitiesQueue(event.getMetadata().getTopicName()).put(entity);
  }

  private void updateMetadataFields(WorkflowInstanceEntity operateEntity, Record zeebeRecord) {
    RecordMetadata metadata = zeebeRecord.getMetadata();

    operateEntity.setPartitionId(metadata.getPartitionId());
    operateEntity.setPosition(metadata.getPosition());
    operateEntity.setTopicName(metadata.getTopicName());
  }

}
