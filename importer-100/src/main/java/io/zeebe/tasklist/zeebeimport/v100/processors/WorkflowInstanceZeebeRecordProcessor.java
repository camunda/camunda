/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.zeebeimport.v100.processors;

import static io.zeebe.protocol.record.intent.WorkflowInstanceIntent.ELEMENT_ACTIVATING;
import static io.zeebe.protocol.record.intent.WorkflowInstanceIntent.ELEMENT_COMPLETED;
import static io.zeebe.protocol.record.intent.WorkflowInstanceIntent.ELEMENT_TERMINATED;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.tasklist.entities.FlowNodeInstanceEntity;
import io.zeebe.tasklist.entities.FlowNodeType;
import io.zeebe.tasklist.entities.WorkflowInstanceEntity;
import io.zeebe.tasklist.entities.WorkflowInstanceState;
import io.zeebe.tasklist.es.schema.indices.FlowNodeInstanceIndex;
import io.zeebe.tasklist.es.schema.indices.WorkflowInstanceIndex;
import io.zeebe.tasklist.exceptions.PersistenceException;
import io.zeebe.tasklist.util.ConversionUtils;
import io.zeebe.tasklist.util.DateUtil;
import io.zeebe.tasklist.util.ElasticsearchUtil;
import io.zeebe.tasklist.zeebeimport.v100.record.value.WorkflowInstanceRecordValueImpl;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class WorkflowInstanceZeebeRecordProcessor {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(WorkflowInstanceZeebeRecordProcessor.class);

  private static final Set<String> FLOW_NODE_STATES = new HashSet<>();
  private static final Set<String> WORKFLOW_INSTANCE_STATES = new HashSet<>();

  private static final List<BpmnElementType> VARIABLE_SCOPE_TYPES =
      Arrays.asList(
          BpmnElementType.PROCESS,
          BpmnElementType.SUB_PROCESS,
          BpmnElementType.SERVICE_TASK,
          BpmnElementType.MULTI_INSTANCE_BODY);

  static {
    FLOW_NODE_STATES.add(ELEMENT_ACTIVATING.name());
    WORKFLOW_INSTANCE_STATES.add(ELEMENT_COMPLETED.name());
    WORKFLOW_INSTANCE_STATES.add(ELEMENT_TERMINATED.name());
  }

  @Autowired private ObjectMapper objectMapper;

  @Autowired private FlowNodeInstanceIndex flowNodeInstanceIndex;

  @Autowired private WorkflowInstanceIndex workflowInstanceIndex;

  public void processWorkflowInstanceRecord(Record record, BulkRequest bulkRequest)
      throws PersistenceException {

    final WorkflowInstanceRecordValueImpl recordValue =
        (WorkflowInstanceRecordValueImpl) record.getValue();
    if (isVariableScopeType(recordValue) && FLOW_NODE_STATES.contains(record.getIntent().name())) {
      final FlowNodeInstanceEntity flowNodeInstance = createFlowNodeInstance(record);
      bulkRequest.add(getFlowNodeInstanceQuery(flowNodeInstance));
    }

    if (isProcessEvent(recordValue)
        && WORKFLOW_INSTANCE_STATES.contains(record.getIntent().name())) {
      final WorkflowInstanceEntity workflowInstanceEntity = createWorkflowInstance(record);
      bulkRequest.add(getWorkflowInstanceQuery(workflowInstanceEntity));
    }
  }

  private WorkflowInstanceEntity createWorkflowInstance(final Record record) {
    final WorkflowInstanceEntity entity = new WorkflowInstanceEntity();
    entity.setId(ConversionUtils.toStringOrNull(record.getKey()));
    entity.setKey(record.getKey());
    entity.setPartitionId(record.getPartitionId());
    if (ELEMENT_COMPLETED.name().equals(record.getIntent().name())) {
      entity.setState(WorkflowInstanceState.COMPLETED);
    } else if (ELEMENT_TERMINATED.name().equals(record.getIntent().name())) {
      entity.setState(WorkflowInstanceState.CANCELED);
    }
    entity.setEndDate(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(record.getTimestamp())));
    return entity;
  }

  private FlowNodeInstanceEntity createFlowNodeInstance(Record record) {
    final WorkflowInstanceRecordValueImpl recordValue =
        (WorkflowInstanceRecordValueImpl) record.getValue();
    final FlowNodeInstanceEntity entity = new FlowNodeInstanceEntity();
    entity.setId(ConversionUtils.toStringOrNull(record.getKey()));
    entity.setKey(record.getKey());
    entity.setPartitionId(record.getPartitionId());
    entity.setWorkflowInstanceId(String.valueOf(recordValue.getWorkflowInstanceKey()));
    entity.setParentFlowNodeId(String.valueOf(recordValue.getFlowScopeKey()));
    entity.setType(
        FlowNodeType.fromZeebeBpmnElementType(
            recordValue.getBpmnElementType() == null
                ? null
                : recordValue.getBpmnElementType().name()));
    entity.setPosition(record.getPosition());
    return entity;
  }

  private IndexRequest getFlowNodeInstanceQuery(FlowNodeInstanceEntity entity)
      throws PersistenceException {
    try {
      LOGGER.debug("Flow node instance: id {}", entity.getId());

      return new IndexRequest(
              flowNodeInstanceIndex.getIndexName(), ElasticsearchUtil.ES_INDEX_TYPE, entity.getId())
          .source(objectMapper.writeValueAsString(entity), XContentType.JSON);
    } catch (IOException e) {
      throw new PersistenceException(
          String.format(
              "Error preparing the query to index flow node instance [%s]", entity.getId()),
          e);
    }
  }

  private IndexRequest getWorkflowInstanceQuery(final WorkflowInstanceEntity entity)
      throws PersistenceException {
    try {
      LOGGER.debug("Workflow instance: id {}", entity.getId());

      return new IndexRequest(
              workflowInstanceIndex.getIndexName(), ElasticsearchUtil.ES_INDEX_TYPE, entity.getId())
          .source(objectMapper.writeValueAsString(entity), XContentType.JSON);
    } catch (IOException e) {
      throw new PersistenceException(
          String.format(
              "Error preparing the query to index workflow instance [%s]w", entity.getId()),
          e);
    }
  }

  private boolean isVariableScopeType(WorkflowInstanceRecordValueImpl recordValue) {
    final BpmnElementType bpmnElementType = recordValue.getBpmnElementType();
    if (bpmnElementType == null) {
      return false;
    }
    return VARIABLE_SCOPE_TYPES.contains(bpmnElementType);
  }

  private boolean isProcessEvent(WorkflowInstanceRecordValueImpl recordValue) {
    return isOfType(recordValue, BpmnElementType.PROCESS);
  }

  private boolean isOfType(WorkflowInstanceRecordValueImpl recordValue, BpmnElementType type) {
    final BpmnElementType bpmnElementType = recordValue.getBpmnElementType();
    if (bpmnElementType == null) {
      return false;
    }
    return bpmnElementType.equals(type);
  }
}
