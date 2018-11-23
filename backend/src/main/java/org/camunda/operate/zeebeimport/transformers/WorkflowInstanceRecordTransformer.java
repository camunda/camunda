/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import org.camunda.operate.zeebeimport.cache.WorkflowCache;
import org.camunda.operate.zeebeimport.record.value.WorkflowInstanceRecordValueImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import io.zeebe.exporter.record.Record;
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
  private static final Set<String> WI_STATES = new HashSet<>();
  private static final String WI_START_STATE;
  private static final Set<String> WI_FINISH_STATES = new HashSet<>();

  //these event states end up in changes in activity instance entity
  private static final Set<String> AI_STATES = new HashSet<>();
  private static final Set<String> AI_FINISH_STATES = new HashSet<>();
  //these event states mean that activities startDate and endDate should be recorded at once
  private static final Set<String> AI_START_END_STATES = new HashSet<>();

  static {
    WI_START_STATE = ELEMENT_ACTIVATED.name();

    WI_FINISH_STATES.add(ELEMENT_COMPLETED.name());
    WI_FINISH_STATES.add(ELEMENT_TERMINATED.name());

    //    WI_STATES.add(PAYLOAD_UPDATED);        //to record changed payload

    WI_STATES.add(WI_START_STATE);
    WI_STATES.addAll(WI_FINISH_STATES);

    AI_FINISH_STATES.add(ELEMENT_COMPLETED.name());
    AI_FINISH_STATES.add(ELEMENT_TERMINATED.name());

    AI_START_END_STATES.add(START_EVENT_OCCURRED.name());
    AI_START_END_STATES.add(END_EVENT_OCCURRED.name());
    AI_START_END_STATES.add(GATEWAY_ACTIVATED.name());

    AI_STATES.add(ELEMENT_READY.name());
    AI_STATES.add(ELEMENT_ACTIVATED.name());
    AI_STATES.add(ELEMENT_COMPLETING.name());

    AI_STATES.addAll(AI_FINISH_STATES);
    AI_STATES.addAll(AI_START_END_STATES);
  }

  @Autowired
  private PayloadUtil payloadUtil;

  @Autowired
  private WorkflowCache workflowCache;

  @Override
  public List<OperateZeebeEntity> convert(Record record) {

//TODO    ZeebeUtil.ALL_EVENTS_LOGGER.debug(record.toJson());

    List<OperateZeebeEntity> entitiesToPersist = new ArrayList<>();

    final String intentStr = record.getMetadata().getIntent().name();

//    if (WI_STATES.contains(intentStr) || AI_STATES.contains(intentStr)) {
      final OperateZeebeEntity event = convertEvent(record);
      if (event != null) {
        entitiesToPersist.add(event);
      }
//    }

    WorkflowInstanceRecordValueImpl recordValue = (WorkflowInstanceRecordValueImpl)record.getValue();

    if (WI_STATES.contains(intentStr) && record.getKey() == recordValue.getWorkflowInstanceKey()
      //we also need 2 activity events to record payload
      || intentStr.equals(ELEMENT_ACTIVATED.name()) || intentStr.equals(ELEMENT_COMPLETED.name())) {
      entitiesToPersist.add(convertWorkflowInstanceRecord(record));
    }
    if (AI_STATES.contains(intentStr) && record.getKey() != recordValue.getWorkflowInstanceKey()) {
      entitiesToPersist.add(convertActivityInstanceEvent(record));
    }

    if (intentStr.equals(SEQUENCE_FLOW_TAKEN.name())) {
      entitiesToPersist.add(convertSequenceFlowTakenEvent(record));
    }

    return entitiesToPersist;

  }

  private OperateZeebeEntity convertSequenceFlowTakenEvent(Record record) {
    SequenceFlowEntity sequenceFlow = new SequenceFlowEntity();
    sequenceFlow.setId(IdUtil.getId(record));
    sequenceFlow.setKey(record.getKey());
    sequenceFlow.setPartitionId(record.getMetadata().getPartitionId());
    WorkflowInstanceRecordValueImpl recordValue = (WorkflowInstanceRecordValueImpl)record.getValue();
    sequenceFlow.setActivityId(recordValue.getElementId());
    sequenceFlow.setWorkflowInstanceId(IdUtil.getId(recordValue.getWorkflowInstanceKey(), record));
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
      eventEntity.setWorkflowInstanceId(IdUtil.getId(recordValue.getWorkflowInstanceKey(), record));
      eventEntity.setBpmnProcessId(recordValue.getBpmnProcessId());

      if (recordValue.getElementId() != null) {
        eventEntity.setActivityId(recordValue.getElementId());
      }

      if (record.getKey() != recordValue.getWorkflowInstanceKey()) {
        eventEntity.setActivityInstanceId(IdUtil.getId(record));
      }

      return eventEntity;
    }
    return null;
  }

  private OperateZeebeEntity convertActivityInstanceEvent(Record record) {
//TODO    logger.debug(event.toJson());

    WorkflowInstanceRecordValueImpl recordValue = (WorkflowInstanceRecordValueImpl)record.getValue();

    ActivityInstanceEntity activityInstanceEntity = new ActivityInstanceEntity();
    activityInstanceEntity.setId(IdUtil.getId(record));
    activityInstanceEntity.setKey(record.getKey());
    activityInstanceEntity.setPartitionId(record.getMetadata().getPartitionId());
    activityInstanceEntity.setActivityId(recordValue.getElementId());
    activityInstanceEntity.setWorkflowInstanceId(IdUtil.getId(recordValue.getWorkflowInstanceKey(), record));
    final String intentStr = record.getMetadata().getIntent().name();
    if (AI_FINISH_STATES.contains(intentStr)) {
      activityInstanceEntity.setEndDate(DateUtil.toOffsetDateTime(record.getTimestamp()));
      if (intentStr.equals(ELEMENT_TERMINATED.name())) {
        activityInstanceEntity.setState(ActivityState.TERMINATED);
      } else {
        activityInstanceEntity.setState(ActivityState.COMPLETED);
      }
    } else if (AI_START_END_STATES.contains(intentStr)) {
      activityInstanceEntity.setStartDate(DateUtil.toOffsetDateTime(record.getTimestamp()));
      activityInstanceEntity.setEndDate(DateUtil.toOffsetDateTime(record.getTimestamp()));
      activityInstanceEntity.setState(ActivityState.COMPLETED);
    } else {
      //we can set start date without checking, which specific event it is, because when persisting to ELS, we do not UPDATE stard date field, only INSERT
      //-> 1st event to come will persist start date for the activity
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
    entity.setId(IdUtil.getId(recordValue.getWorkflowInstanceKey(), record));
    entity.setKey(recordValue.getWorkflowInstanceKey());
    entity.setPartitionId(record.getMetadata().getPartitionId());
    entity.setWorkflowId(String.valueOf(recordValue.getWorkflowKey()));
    entity.setBpmnProcessId(recordValue.getBpmnProcessId());

    final String intentStr = record.getMetadata().getIntent().name();
    if (WI_FINISH_STATES.contains(intentStr) && recordValue.getWorkflowInstanceKey() == record.getKey()) {   //TODO
      entity.setEndDate(DateUtil.toOffsetDateTime(record.getTimestamp()));
      if (intentStr.equals(ELEMENT_TERMINATED.name())) {
        entity.setState(WorkflowInstanceState.CANCELED);
      } else {
        entity.setState(WorkflowInstanceState.COMPLETED);
      }
    } else if (WI_START_STATE.equals(intentStr)){
      entity.setState(WorkflowInstanceState.ACTIVE);
      entity.setStartDate(DateUtil.toOffsetDateTime(record.getTimestamp()));
    } else {
      entity.setState(WorkflowInstanceState.ACTIVE);
    }

    //find out workflow name and version
    entity.setWorkflowName(workflowCache.getWorkflowName(entity.getWorkflowId(), recordValue.getBpmnProcessId()));
    entity.setWorkflowVersion(workflowCache.getWorkflowVersion(entity.getWorkflowId(), recordValue.getBpmnProcessId()));

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
