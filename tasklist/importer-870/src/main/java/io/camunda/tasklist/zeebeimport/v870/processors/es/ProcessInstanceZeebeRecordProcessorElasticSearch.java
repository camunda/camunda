/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport.v870.processors.es;

import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_ACTIVATING;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_COMPLETED;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_TERMINATED;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.entities.FlowNodeInstanceEntity;
import io.camunda.tasklist.entities.FlowNodeType;
import io.camunda.tasklist.entities.ProcessInstanceEntity;
import io.camunda.tasklist.entities.ProcessInstanceState;
import io.camunda.tasklist.exceptions.PersistenceException;
import io.camunda.tasklist.schema.indices.FlowNodeInstanceIndex;
import io.camunda.tasklist.schema.indices.ProcessInstanceIndex;
import io.camunda.tasklist.util.ConversionUtils;
import io.camunda.tasklist.util.DateUtil;
import io.camunda.tasklist.zeebeimport.v870.record.value.ProcessInstanceRecordValueImpl;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class ProcessInstanceZeebeRecordProcessorElasticSearch {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ProcessInstanceZeebeRecordProcessorElasticSearch.class);

  private static final Set<String> FLOW_NODE_STATES = new HashSet<>();
  private static final Set<String> PROCESS_INSTANCE_STATES = new HashSet<>();

  private static final List<BpmnElementType> VARIABLE_SCOPE_TYPES =
      Arrays.asList(
          BpmnElementType.PROCESS,
          BpmnElementType.SUB_PROCESS,
          BpmnElementType.EVENT_SUB_PROCESS,
          BpmnElementType.SERVICE_TASK,
          BpmnElementType.USER_TASK,
          BpmnElementType.MULTI_INSTANCE_BODY,
          BpmnElementType.AD_HOC_SUB_PROCESS);

  static {
    FLOW_NODE_STATES.add(ELEMENT_ACTIVATING.name());
    PROCESS_INSTANCE_STATES.add(ELEMENT_COMPLETED.name());
    PROCESS_INSTANCE_STATES.add(ELEMENT_TERMINATED.name());
  }

  @Autowired
  @Qualifier("tasklistObjectMapper")
  private ObjectMapper objectMapper;

  @Autowired private FlowNodeInstanceIndex flowNodeInstanceIndex;

  @Autowired private ProcessInstanceIndex processInstanceIndex;

  public void processProcessInstanceRecord(final Record record, final BulkRequest bulkRequest)
      throws PersistenceException {

    final ProcessInstanceRecordValueImpl recordValue =
        (ProcessInstanceRecordValueImpl) record.getValue();
    if (isVariableScopeType(recordValue) && FLOW_NODE_STATES.contains(record.getIntent().name())) {
      final FlowNodeInstanceEntity flowNodeInstance = createFlowNodeInstance(record);
      bulkRequest.add(getFlowNodeInstanceQuery(flowNodeInstance));
    }

    if (isProcessEvent(recordValue)
        && PROCESS_INSTANCE_STATES.contains(record.getIntent().name())) {
      final ProcessInstanceEntity processInstanceEntity = createProcessInstance(record);
      bulkRequest.add(getProcessInstanceQuery(processInstanceEntity));
    }
  }

  private ProcessInstanceEntity createProcessInstance(final Record record) {
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
    return entity;
  }

  private FlowNodeInstanceEntity createFlowNodeInstance(final Record record) {
    final ProcessInstanceRecordValueImpl recordValue =
        (ProcessInstanceRecordValueImpl) record.getValue();
    final FlowNodeInstanceEntity entity = new FlowNodeInstanceEntity();
    entity.setId(ConversionUtils.toStringOrNull(record.getKey()));
    entity.setKey(record.getKey());
    entity.setPartitionId(record.getPartitionId());
    entity.setProcessInstanceId(String.valueOf(recordValue.getProcessInstanceKey()));
    entity.setParentFlowNodeId(String.valueOf(recordValue.getFlowScopeKey()));
    entity.setType(
        FlowNodeType.fromZeebeBpmnElementType(
            recordValue.getBpmnElementType() == null
                ? null
                : recordValue.getBpmnElementType().name()));
    entity.setPosition(record.getPosition());
    return entity;
  }

  private IndexRequest getFlowNodeInstanceQuery(final FlowNodeInstanceEntity entity)
      throws PersistenceException {
    try {
      LOGGER.debug("Flow node instance: id {}", entity.getId());

      return new IndexRequest(flowNodeInstanceIndex.getFullQualifiedName())
          .id(entity.getId())
          .source(objectMapper.writeValueAsString(entity), XContentType.JSON);
    } catch (final IOException e) {
      throw new PersistenceException(
          String.format(
              "Error preparing the query to index flow node instance [%s]", entity.getId()),
          e);
    }
  }

  private IndexRequest getProcessInstanceQuery(final ProcessInstanceEntity entity)
      throws PersistenceException {
    try {
      LOGGER.debug("Process instance: id {}", entity.getId());

      return new IndexRequest()
          .index(processInstanceIndex.getFullQualifiedName())
          .id(entity.getId())
          .source(objectMapper.writeValueAsString(entity), XContentType.JSON);
    } catch (final IOException e) {
      throw new PersistenceException(
          String.format(
              "Error preparing the query to index process instance [%s]w", entity.getId()),
          e);
    }
  }

  private boolean isVariableScopeType(final ProcessInstanceRecordValueImpl recordValue) {
    final BpmnElementType bpmnElementType = recordValue.getBpmnElementType();
    if (bpmnElementType == null) {
      return false;
    }
    return VARIABLE_SCOPE_TYPES.contains(bpmnElementType);
  }

  private boolean isProcessEvent(final ProcessInstanceRecordValueImpl recordValue) {
    return isOfType(recordValue, BpmnElementType.PROCESS);
  }

  private boolean isOfType(
      final ProcessInstanceRecordValueImpl recordValue, final BpmnElementType type) {
    final BpmnElementType bpmnElementType = recordValue.getBpmnElementType();
    if (bpmnElementType == null) {
      return false;
    }
    return bpmnElementType.equals(type);
  }
}
