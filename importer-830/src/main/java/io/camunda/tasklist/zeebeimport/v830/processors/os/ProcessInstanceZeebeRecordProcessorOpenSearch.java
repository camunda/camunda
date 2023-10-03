/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.zeebeimport.v830.processors.os;

import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_ACTIVATING;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_COMPLETED;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_TERMINATED;

import io.camunda.tasklist.CommonUtils;
import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.entities.FlowNodeInstanceEntity;
import io.camunda.tasklist.entities.FlowNodeType;
import io.camunda.tasklist.entities.ProcessInstanceEntity;
import io.camunda.tasklist.entities.ProcessInstanceState;
import io.camunda.tasklist.schema.indices.FlowNodeInstanceIndex;
import io.camunda.tasklist.schema.indices.ProcessInstanceIndex;
import io.camunda.tasklist.util.ConversionUtils;
import io.camunda.tasklist.util.DateUtil;
import io.camunda.tasklist.zeebeimport.v830.record.value.ProcessInstanceRecordValueImpl;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.opensearch.client.opensearch.core.bulk.BulkOperation;
import org.opensearch.client.opensearch.core.bulk.IndexOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class ProcessInstanceZeebeRecordProcessorOpenSearch {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ProcessInstanceZeebeRecordProcessorOpenSearch.class);

  private static final Set<String> FLOW_NODE_STATES = new HashSet<>();
  private static final Set<String> PROCESS_INSTANCE_STATES = new HashSet<>();

  private static final List<BpmnElementType> VARIABLE_SCOPE_TYPES =
      Arrays.asList(
          BpmnElementType.PROCESS,
          BpmnElementType.SUB_PROCESS,
          BpmnElementType.EVENT_SUB_PROCESS,
          BpmnElementType.SERVICE_TASK,
          BpmnElementType.USER_TASK,
          BpmnElementType.MULTI_INSTANCE_BODY);

  static {
    FLOW_NODE_STATES.add(ELEMENT_ACTIVATING.name());
    PROCESS_INSTANCE_STATES.add(ELEMENT_COMPLETED.name());
    PROCESS_INSTANCE_STATES.add(ELEMENT_TERMINATED.name());
  }

  @Autowired private FlowNodeInstanceIndex flowNodeInstanceIndex;

  @Autowired private ProcessInstanceIndex processInstanceIndex;

  public void processProcessInstanceRecord(
      Record<ProcessInstanceRecordValueImpl> record, List<BulkOperation> operations) {

    final ProcessInstanceRecordValueImpl recordValue = record.getValue();
    if (isVariableScopeType(recordValue) && FLOW_NODE_STATES.contains(record.getIntent().name())) {
      final FlowNodeInstanceEntity flowNodeInstance = createFlowNodeInstance(record);
      operations.add(getFlowNodeInstanceQuery(flowNodeInstance));
    }

    if (isProcessEvent(recordValue)
        && PROCESS_INSTANCE_STATES.contains(record.getIntent().name())) {
      final ProcessInstanceEntity processInstanceEntity = createProcessInstance(record);
      operations.add(getProcessInstanceQuery(processInstanceEntity));
    }
  }

  private ProcessInstanceEntity createProcessInstance(
      final Record<ProcessInstanceRecordValueImpl> record) {
    final ProcessInstanceEntity entity = new ProcessInstanceEntity();
    entity.setId(ConversionUtils.toStringOrNull(record.getKey()));
    entity.setKey(record.getKey());
    entity.setPartitionId(record.getPartitionId());
    if (ELEMENT_COMPLETED.name().equals(record.getIntent().name())) {
      entity.setState(ProcessInstanceState.COMPLETED);
    } else if (ELEMENT_TERMINATED.name().equals(record.getIntent().name())) {
      entity.setState(ProcessInstanceState.CANCELED);
    }
    entity.setEndDate(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())));
    entity.setTenantId(record.getValue().getTenantId());
    return entity;
  }

  private FlowNodeInstanceEntity createFlowNodeInstance(
      Record<ProcessInstanceRecordValueImpl> record) {
    final ProcessInstanceRecordValueImpl recordValue = record.getValue();
    return new FlowNodeInstanceEntity()
        .setId(ConversionUtils.toStringOrNull(record.getKey()))
        .setKey(record.getKey())
        .setPartitionId(record.getPartitionId())
        .setProcessInstanceId(String.valueOf(recordValue.getProcessInstanceKey()))
        .setParentFlowNodeId(String.valueOf(recordValue.getFlowScopeKey()))
        .setType(
            FlowNodeType.fromZeebeBpmnElementType(
                recordValue.getBpmnElementType() == null
                    ? null
                    : recordValue.getBpmnElementType().name()))
        .setPosition(record.getPosition())
        .setTenantId(recordValue.getTenantId());
  }

  private BulkOperation getFlowNodeInstanceQuery(FlowNodeInstanceEntity entity) {

    LOGGER.debug("Flow node instance: id {}", entity.getId());

    return new BulkOperation.Builder()
        .index(
            IndexOperation.of(
                io ->
                    io.index(flowNodeInstanceIndex.getFullQualifiedName())
                        .id(entity.getId())
                        .document(CommonUtils.getJsonObjectFromEntity(entity))))
        .build();
  }

  private BulkOperation getProcessInstanceQuery(final ProcessInstanceEntity entity) {

    LOGGER.debug("Process instance: id {}", entity.getId());

    return new BulkOperation.Builder()
        .index(
            IndexOperation.of(
                io ->
                    io.index(processInstanceIndex.getFullQualifiedName())
                        .id(entity.getId())
                        .document(CommonUtils.getJsonObjectFromEntity(entity))))
        .build();
  }

  private boolean isVariableScopeType(ProcessInstanceRecordValueImpl recordValue) {
    final BpmnElementType bpmnElementType = recordValue.getBpmnElementType();
    if (bpmnElementType == null) {
      return false;
    }
    return VARIABLE_SCOPE_TYPES.contains(bpmnElementType);
  }

  private boolean isProcessEvent(ProcessInstanceRecordValueImpl recordValue) {
    return isOfType(recordValue, BpmnElementType.PROCESS);
  }

  private boolean isOfType(ProcessInstanceRecordValueImpl recordValue, BpmnElementType type) {
    final BpmnElementType bpmnElementType = recordValue.getBpmnElementType();
    if (bpmnElementType == null) {
      return false;
    }
    return bpmnElementType.equals(type);
  }
}
