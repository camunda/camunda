/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport.v870.processors.es;

import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_ACTIVATING;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.exceptions.PersistenceException;
import io.camunda.tasklist.util.ConversionUtils;
import io.camunda.tasklist.zeebeimport.v870.record.value.ProcessInstanceRecordValueImpl;
import io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.entities.flownode.FlowNodeInstanceEntity;
import io.camunda.webapps.schema.entities.flownode.FlowNodeState;
import io.camunda.webapps.schema.entities.flownode.FlowNodeType;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.update.UpdateRequest;
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

  private static final List<BpmnElementType> VARIABLE_SCOPE_TYPES =
      Arrays.asList(
          BpmnElementType.SUB_PROCESS,
          BpmnElementType.EVENT_SUB_PROCESS,
          BpmnElementType.SERVICE_TASK,
          BpmnElementType.USER_TASK,
          BpmnElementType.MULTI_INSTANCE_BODY,
          BpmnElementType.AD_HOC_SUB_PROCESS);

  static {
    FLOW_NODE_STATES.add(ELEMENT_ACTIVATING.name());
  }

  @Autowired
  @Qualifier("tasklistObjectMapper")
  private ObjectMapper objectMapper;

  @Autowired
  @Qualifier("tasklistFlowNodeInstanceTemplate")
  private FlowNodeInstanceTemplate flowNodeInstanceIndex;

  public void processProcessInstanceRecord(final Record record, final BulkRequest bulkRequest)
      throws PersistenceException {

    final ProcessInstanceRecordValueImpl recordValue =
        (ProcessInstanceRecordValueImpl) record.getValue();
    if (isVariableScopeType(recordValue) && FLOW_NODE_STATES.contains(record.getIntent().name())) {
      final FlowNodeInstanceEntity flowNodeInstance = createFlowNodeInstance(record);
      bulkRequest.add(getFlowNodeInstanceQuery(flowNodeInstance));
    }
  }

  private FlowNodeInstanceEntity createFlowNodeInstance(final Record record) {
    final ProcessInstanceRecordValueImpl recordValue =
        (ProcessInstanceRecordValueImpl) record.getValue();
    final FlowNodeInstanceEntity entity = new FlowNodeInstanceEntity();
    entity.setId(ConversionUtils.toStringOrNull(record.getKey()));
    entity.setKey(record.getKey());
    entity.setPartitionId(record.getPartitionId());
    entity.setProcessInstanceKey(recordValue.getProcessInstanceKey());
    entity.setScopeKey(recordValue.getFlowScopeKey());
    entity.setState(FlowNodeState.ACTIVE);
    entity.setType(
        FlowNodeType.fromZeebeBpmnElementType(
            recordValue.getBpmnElementType() == null
                ? null
                : recordValue.getBpmnElementType().name()));
    entity.setPosition(record.getPosition());
    return entity;
  }

  private UpdateRequest getFlowNodeInstanceQuery(final FlowNodeInstanceEntity entity)
      throws PersistenceException {
    try {
      LOGGER.debug("Flow node instance: id {}", entity.getId());

      return new UpdateRequest(flowNodeInstanceIndex.getFullQualifiedName(), entity.getId())
          .upsert(objectMapper.writeValueAsString(entity), XContentType.JSON)
          .doc(Map.of(FlowNodeInstanceTemplate.KEY, entity.getKey()));
    } catch (final IOException e) {
      throw new PersistenceException(
          String.format(
              "Error preparing the query to index flow node instance [%s]", entity.getId()),
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
}
