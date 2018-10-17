package org.camunda.operate.zeebeimport.transformers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.camunda.operate.entities.ActivityInstanceEntity;
import org.camunda.operate.entities.ActivityState;
import org.camunda.operate.entities.ActivityType;
import org.camunda.operate.entities.EventEntity;
import org.camunda.operate.entities.OperateZeebeEntity;
import org.camunda.operate.entities.SequenceFlowEntity;
import org.camunda.operate.entities.WorkflowInstanceEntity;
import org.camunda.operate.entities.WorkflowInstanceState;
import org.camunda.operate.util.DateUtil;
import org.camunda.operate.util.IdUtil;
import org.camunda.operate.zeebe.payload.PayloadUtil;
import org.camunda.operate.zeebeimport.record.value.WorkflowInstanceRecordValueImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import io.zeebe.exporter.record.Record;
import static io.zeebe.protocol.intent.WorkflowInstanceIntent.CREATED;
import static io.zeebe.protocol.intent.WorkflowInstanceIntent.ELEMENT_ACTIVATED;
import static io.zeebe.protocol.intent.WorkflowInstanceIntent.ELEMENT_COMPLETED;
import static io.zeebe.protocol.intent.WorkflowInstanceIntent.ELEMENT_COMPLETING;
import static io.zeebe.protocol.intent.WorkflowInstanceIntent.ELEMENT_READY;
import static io.zeebe.protocol.intent.WorkflowInstanceIntent.ELEMENT_TERMINATED;
import static io.zeebe.protocol.intent.WorkflowInstanceIntent.END_EVENT_OCCURRED;
import static io.zeebe.protocol.intent.WorkflowInstanceIntent.GATEWAY_ACTIVATED;
import static io.zeebe.protocol.intent.WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN;
import static io.zeebe.protocol.intent.WorkflowInstanceIntent.START_EVENT_OCCURRED;

@Component
public class WorkflowInstanceRecordTransformer implements AbstractRecordTransformer {

  private static final Logger logger = LoggerFactory.getLogger(WorkflowInstanceRecordTransformer.class);

  //these event states end up in changes in workflow instance entity
  private static final Set<String> WORKFLOW_INSTANCE_STATES = new HashSet<>();
  //these event states end up in changes in activity instance entity
  private static final Set<String> ACTIVITY_INSTANCE_STATES = new HashSet<>();
  //these event states mean that workflow instance was finished
  private static final Set<String> WORKFLOW_INSTANCE_FINISH_STATES = new HashSet<>();
  //these event states mean that activity instance was finished
  private static final Set<String> ACTIVITY_INSTANCE_FINISH_STATES = new HashSet<>();
  //these event states mean that activities startDate and endDate should be recorded at once
  private static final Set<String> ACTIVITY_INSTANCE_START_END_STATES = new HashSet<>();

  static {
    WORKFLOW_INSTANCE_FINISH_STATES.add(ELEMENT_COMPLETED.name());
    WORKFLOW_INSTANCE_FINISH_STATES.add(ELEMENT_TERMINATED.name());

    ACTIVITY_INSTANCE_FINISH_STATES.add(ELEMENT_COMPLETED.name());
    ACTIVITY_INSTANCE_FINISH_STATES.add(ELEMENT_TERMINATED.name());

    ACTIVITY_INSTANCE_START_END_STATES.add(START_EVENT_OCCURRED.name());
    ACTIVITY_INSTANCE_START_END_STATES.add(END_EVENT_OCCURRED.name());
    ACTIVITY_INSTANCE_START_END_STATES.add(GATEWAY_ACTIVATED.name());

    WORKFLOW_INSTANCE_STATES.add(CREATED.name());
    WORKFLOW_INSTANCE_STATES.add(ELEMENT_COMPLETED.name());     //to record changed payload
    WORKFLOW_INSTANCE_STATES.add(ELEMENT_ACTIVATED.name());     //to record changed payload
//    WORKFLOW_INSTANCE_STATES.add(PAYLOAD_UPDATED);        //to record changed payload
    WORKFLOW_INSTANCE_STATES.addAll(WORKFLOW_INSTANCE_FINISH_STATES);

    ACTIVITY_INSTANCE_STATES.add(ELEMENT_READY.name());
    ACTIVITY_INSTANCE_STATES.add(ELEMENT_ACTIVATED.name());
    ACTIVITY_INSTANCE_STATES.add(ELEMENT_COMPLETING.name());
    ACTIVITY_INSTANCE_STATES.addAll(ACTIVITY_INSTANCE_FINISH_STATES);
    ACTIVITY_INSTANCE_STATES.addAll(ACTIVITY_INSTANCE_START_END_STATES);
  }

  @Autowired
  private PayloadUtil payloadUtil;

  @Override
  public List<OperateZeebeEntity> convert(Record record) {

//TODO    ZeebeUtil.ALL_EVENTS_LOGGER.debug(record.toJson());

    List<OperateZeebeEntity> entitiesToPersist = new ArrayList<>();

    final String intentStr = record.getMetadata().getIntent().name();

//    if (WORKFLOW_INSTANCE_STATES.contains(intentStr) || ACTIVITY_INSTANCE_STATES.contains(intentStr)) {
      final OperateZeebeEntity event = convertEvent(record);
      if (event != null) {
        entitiesToPersist.add(event);
      }
//    }

    WorkflowInstanceRecordValueImpl recordValue = (WorkflowInstanceRecordValueImpl)record.getValue();



    if (WORKFLOW_INSTANCE_STATES.contains(intentStr)) {

      entitiesToPersist.add(convertWorkflowInstanceRecord(record));

    }
    if (ACTIVITY_INSTANCE_STATES.contains(intentStr) && record.getKey() != recordValue.getWorkflowInstanceKey()) {

      entitiesToPersist.add(convertActivityInstanceEvent(record));

    }
    if (intentStr.equals(SEQUENCE_FLOW_TAKEN.name())) {
      entitiesToPersist.add(convertSequenceFlowTakenEvent(record));
    }

    return entitiesToPersist;

  }

  private OperateZeebeEntity convertSequenceFlowTakenEvent(Record record) {
    SequenceFlowEntity sequenceFlow = new SequenceFlowEntity();
    sequenceFlow.setId(IdUtil.createId(record.getKey(), record.getMetadata().getPartitionId()));
    sequenceFlow.setKey(record.getKey());
    sequenceFlow.setPartitionId(record.getMetadata().getPartitionId());
    WorkflowInstanceRecordValueImpl recordValue = (WorkflowInstanceRecordValueImpl)record.getValue();
    sequenceFlow.setActivityId(recordValue.getActivityId());
    sequenceFlow.setWorkflowInstanceId(IdUtil.createId(recordValue.getWorkflowInstanceKey(), record.getMetadata().getPartitionId()));
    return sequenceFlow;
  }

  private OperateZeebeEntity convertEvent(Record record) {
    final String intentStr = record.getMetadata().getIntent().name();

    //we will store sequence flows separately, no need to store them in events
    if (!intentStr.equals(SEQUENCE_FLOW_TAKEN.name())) {

      EventEntity eventEntity = new EventEntity();

      loadEventGeneralData(record, eventEntity);

      WorkflowInstanceRecordValueImpl recordValue = (WorkflowInstanceRecordValueImpl)record.getValue();

      eventEntity.setPayload(recordValue.getPayload());
      eventEntity.setWorkflowId(String.valueOf(recordValue.getWorkflowKey()));
      eventEntity.setWorkflowInstanceId(IdUtil.createId(recordValue.getWorkflowInstanceKey(), record.getMetadata().getPartitionId()));
      eventEntity.setBpmnProcessId(recordValue.getBpmnProcessId());

      if (recordValue.getActivityId() != null) {
        eventEntity.setActivityId(recordValue.getActivityId());
      }

      if (record.getKey() != recordValue.getWorkflowInstanceKey()) {
        eventEntity.setActivityInstanceId(IdUtil.createId(record.getKey(), record.getMetadata().getPartitionId()));
      }

      return eventEntity;
    }
    return null;
  }

  private OperateZeebeEntity convertActivityInstanceEvent(Record record) {
//TODO    logger.debug(event.toJson());

    WorkflowInstanceRecordValueImpl recordValue = (WorkflowInstanceRecordValueImpl)record.getValue();

    ActivityInstanceEntity activityInstanceEntity = new ActivityInstanceEntity();
    activityInstanceEntity.setId(IdUtil.createId(record.getKey(), record.getMetadata().getPartitionId()));
    activityInstanceEntity.setKey(record.getKey());
    activityInstanceEntity.setPartitionId(record.getMetadata().getPartitionId());
    activityInstanceEntity.setActivityId(recordValue.getActivityId());
    activityInstanceEntity.setWorkflowInstanceId(IdUtil.createId(recordValue.getWorkflowInstanceKey(), record.getMetadata().getPartitionId()));
    final String intentStr = record.getMetadata().getIntent().name();
    if (ACTIVITY_INSTANCE_FINISH_STATES.contains(intentStr)) {
      activityInstanceEntity.setEndDate(DateUtil.toOffsetDateTime(record.getTimestamp()));
      if (intentStr.equals(ELEMENT_TERMINATED.name())) {
        activityInstanceEntity.setState(ActivityState.TERMINATED);
      } else {
        activityInstanceEntity.setState(ActivityState.COMPLETED);
      }
    } else if (ACTIVITY_INSTANCE_START_END_STATES.contains(intentStr)) {
      activityInstanceEntity.setStartDate(DateUtil.toOffsetDateTime(record.getTimestamp()));
      activityInstanceEntity.setEndDate(DateUtil.toOffsetDateTime(record.getTimestamp()));
      activityInstanceEntity.setState(ActivityState.COMPLETED);
    } else {
      activityInstanceEntity.setStartDate(DateUtil.toOffsetDateTime(record.getTimestamp()));
      activityInstanceEntity.setState(ActivityState.ACTIVE);
    }

    if (intentStr.equals(START_EVENT_OCCURRED.name())) {
      activityInstanceEntity.setType(ActivityType.START_EVENT);
    } else if (intentStr.equals(END_EVENT_OCCURRED.name())) {
      activityInstanceEntity.setType(ActivityType.END_EVENT);
    } else if (intentStr.equals(GATEWAY_ACTIVATED.name())) {
      activityInstanceEntity.setType(ActivityType.GATEWAY);
    }

    return activityInstanceEntity;
  }

  private OperateZeebeEntity convertWorkflowInstanceRecord(Record record) {
//TODO    logger.debug(record.toJson());

    WorkflowInstanceRecordValueImpl recordValue = (WorkflowInstanceRecordValueImpl)record.getValue();

    WorkflowInstanceEntity entity = new WorkflowInstanceEntity();
    entity.setId(IdUtil.createId(recordValue.getWorkflowInstanceKey(), record.getMetadata().getPartitionId()));
    entity.setKey(recordValue.getWorkflowInstanceKey());
    entity.setPartitionId(record.getMetadata().getPartitionId());
    entity.setWorkflowId(String.valueOf(recordValue.getWorkflowKey()));
    entity.setBusinessKey(recordValue.getBpmnProcessId());
    final String intentStr = record.getMetadata().getIntent().name();
    if (WORKFLOW_INSTANCE_FINISH_STATES.contains(intentStr) && recordValue.getWorkflowInstanceKey() == record.getKey()) {
      entity.setEndDate(DateUtil.toOffsetDateTime(record.getTimestamp()));
      if (intentStr.equals(ELEMENT_TERMINATED.name())) {
        entity.setState(WorkflowInstanceState.CANCELED);
      } else {
        entity.setState(WorkflowInstanceState.COMPLETED);
      }
    } else {
      entity.setState(WorkflowInstanceState.ACTIVE);
      entity.setStartDate(DateUtil.toOffsetDateTime(record.getTimestamp()));
    }

    entity.setPosition(record.getPosition());

    processPayload(recordValue.getPayload(), entity);

    return entity;
  }

  private void processPayload(String payload, WorkflowInstanceEntity entity) {
    try {
      final Map<String, Object> variablesMap = payloadUtil.parsePayload(payload);
      entity.setStringVariables(payloadUtil.extractStringVariables(variablesMap));
      entity.setLongVariables(payloadUtil.extractLongVariables(variablesMap));
      entity.setDoubleVariables(payloadUtil.extractDoubleVariables(variablesMap));
      entity.setBooleanVariables(payloadUtil.extractBooleanVariables(variablesMap));
      //validate number of parsed variables
      if (entity.countVariables() < variablesMap.size()) {
        logger.warn("Not all variables were parsed from payload for workflow instance {}.", entity.getId());
      }
    } catch (IOException e) {
      logger.warn(String.format("Unable to parse payload for workflow instance %s.", entity.getId()), e);
    }
  }

}
