/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.zeebeimport.v24.processors;

import static io.zeebe.protocol.record.intent.WorkflowInstanceIntent.ELEMENT_ACTIVATING;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.tasklist.entities.FlowNodeInstanceEntity;
import io.zeebe.tasklist.entities.FlowNodeType;
import io.zeebe.tasklist.es.schema.templates.FlowNodeInstanceTemplate;
import io.zeebe.tasklist.exceptions.PersistenceException;
import io.zeebe.tasklist.util.ConversionUtils;
import io.zeebe.tasklist.util.ElasticsearchUtil;
import io.zeebe.tasklist.zeebeimport.v24.record.value.WorkflowInstanceRecordValueImpl;
import java.io.IOException;
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

  private static final List<BpmnElementType> VARIABLE_SCOPE_TYPES =
      Arrays.asList(
          BpmnElementType.PROCESS,
          BpmnElementType.SUB_PROCESS,
          BpmnElementType.SERVICE_TASK,
          BpmnElementType.MULTI_INSTANCE_BODY);

  static {
    FLOW_NODE_STATES.add(ELEMENT_ACTIVATING.name());
  }

  @Autowired private ObjectMapper objectMapper;

  @Autowired private FlowNodeInstanceTemplate flowNodeInstanceTemplate;

  public void processWorkflowInstanceRecord(Record record, BulkRequest bulkRequest)
      throws PersistenceException {

    if (isVariableScopeType((WorkflowInstanceRecordValueImpl) record.getValue())
        && FLOW_NODE_STATES.contains(record.getIntent().name())) {
      final FlowNodeInstanceEntity flowNodeInstance = createFlowNodeInstance(record);
      bulkRequest.add(getFlowNodeInstanceQuery(flowNodeInstance));
    }
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
      LOGGER.debug("Flow node instance for list view: id {}", entity.getId());

      return new IndexRequest(
              flowNodeInstanceTemplate.getMainIndexName(),
              ElasticsearchUtil.ES_INDEX_TYPE,
              entity.getId())
          .source(objectMapper.writeValueAsString(entity), XContentType.JSON);

    } catch (IOException e) {
      LOGGER.error("Error preparing the query to upsert activity instance for list view", e);
      throw new PersistenceException(
          String.format(
              "Error preparing the query to upsert activity instance [%s]  for list view",
              entity.getId()),
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
}
