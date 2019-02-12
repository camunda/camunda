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
import io.zeebe.protocol.BpmnElementType;
import static io.zeebe.protocol.intent.WorkflowInstanceIntent.ELEMENT_ACTIVATED;
import static io.zeebe.protocol.intent.WorkflowInstanceIntent.ELEMENT_COMPLETED;
import static io.zeebe.protocol.intent.WorkflowInstanceIntent.ELEMENT_TERMINATED;
import static io.zeebe.protocol.intent.WorkflowInstanceIntent.PAYLOAD_UPDATED;
import static io.zeebe.protocol.intent.WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN;

@Component
public class WorkflowInstanceRecordTransformer implements AbstractRecordTransformer {

  private static final Logger logger = LoggerFactory.getLogger(WorkflowInstanceRecordTransformer.class);

  //these event states end up in changes in workflow instance entity
  private static final Set<String> STATES_TO_LOAD = new HashSet<>();
  private static final String START_STATES;
  private static final Set<String> FINISH_STATES = new HashSet<>();

  static {
    START_STATES = ELEMENT_ACTIVATED.name();

    FINISH_STATES.add(ELEMENT_COMPLETED.name());
    FINISH_STATES.add(ELEMENT_TERMINATED.name());

    //    STATES_TO_LOAD.add(PAYLOAD_UPDATED);        //to record changed payload

    STATES_TO_LOAD.add(START_STATES);
    STATES_TO_LOAD.addAll(FINISH_STATES);
    STATES_TO_LOAD.add(PAYLOAD_UPDATED.name());
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

    if (STATES_TO_LOAD.contains(intentStr)) {
      final OperateZeebeEntity event = convertEvent(record);
      if (event != null) {
        entitiesToPersist.add(event);
      }
    }

    WorkflowInstanceRecordValueImpl recordValue = (WorkflowInstanceRecordValueImpl)record.getValue();

    if (STATES_TO_LOAD.contains(intentStr) && isProcessEvent(recordValue.getBpmnElementType())
      //we also need 2 activity events to record payload
      || intentStr.equals(ELEMENT_ACTIVATED.name()) || intentStr.equals(ELEMENT_COMPLETED.name())) {
      entitiesToPersist.add(convertWorkflowInstanceRecord(record));
    }

    if (intentStr.equals(SEQUENCE_FLOW_TAKEN.name())) {
      entitiesToPersist.add(convertSequenceFlowTakenEvent(record));
    }

    return entitiesToPersist;

  }

  private boolean isProcessEvent(BpmnElementType bpmnElementType) {
    if (bpmnElementType == null) {
      return false;
    }
    return bpmnElementType.equals(BpmnElementType.PROCESS);
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
    if (FINISH_STATES.contains(intentStr) && isProcessEvent(recordValue.getBpmnElementType())) {
      entity.setEndDate(DateUtil.toOffsetDateTime(record.getTimestamp()));
      if (intentStr.equals(ELEMENT_TERMINATED.name())) {
        entity.setState(WorkflowInstanceState.CANCELED);
      } else {
        entity.setState(WorkflowInstanceState.COMPLETED);
      }
    } else if (START_STATES.equals(intentStr) && isProcessEvent(recordValue.getBpmnElementType())){
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
