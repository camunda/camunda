/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.operate.exporter.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.operate.entities.FlowNodeInstanceEntity;
import io.camunda.operate.entities.FlowNodeState;
import io.camunda.operate.entities.FlowNodeType;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.templates.FlowNodeInstanceTemplate;
import io.camunda.operate.store.elasticsearch.NewElasticsearchBatchRequest;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class FlowNodeInstanceProcessInstanceHandlerTest {
  @Mock private FlowNodeInstanceTemplate mockFlowNodeInstanceTemplate;

  private FlowNodeInstanceProcessInstanceHandler underTest;

  @BeforeEach
  public void setup() {
    underTest = new FlowNodeInstanceProcessInstanceHandler(mockFlowNodeInstanceTemplate);
  }

  @Test
  public void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.PROCESS_INSTANCE);
  }

  @Test
  public void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(FlowNodeInstanceEntity.class);
  }

  @Test
  public void testHandlesValidRecordForWrongBpmnElementType() {
    final Record<ProcessInstanceRecordValue> mockSequenceFlowRecord = Mockito.mock(Record.class);
    final ProcessInstanceRecordValue mockSequenceFlowRecordValue =
        Mockito.mock(ProcessInstanceRecordValue.class);
    when(mockSequenceFlowRecord.getValue()).thenReturn(mockSequenceFlowRecordValue);

    when(mockSequenceFlowRecord.getIntent()).thenReturn(ProcessInstanceIntent.ELEMENT_MIGRATED);
    when(mockSequenceFlowRecordValue.getBpmnElementType()).thenReturn(BpmnElementType.PROCESS);
    assertThat(underTest.handlesRecord(mockSequenceFlowRecord)).isFalse();
  }

  @Test
  public void testHandlesValidRecordForNullBpmnElementType() {
    final Record<ProcessInstanceRecordValue> mockSequenceFlowRecord = Mockito.mock(Record.class);
    final ProcessInstanceRecordValue mockSequenceFlowRecordValue =
        Mockito.mock(ProcessInstanceRecordValue.class);
    when(mockSequenceFlowRecord.getValue()).thenReturn(mockSequenceFlowRecordValue);

    when(mockSequenceFlowRecord.getIntent()).thenReturn(ProcessInstanceIntent.ELEMENT_MIGRATED);
    when(mockSequenceFlowRecordValue.getBpmnElementType()).thenReturn(null);
    assertThat(underTest.handlesRecord(mockSequenceFlowRecord)).isTrue();
  }

  @Test
  public void testHandlesValidRecordForValidStateAndType() {
    final Record<ProcessInstanceRecordValue> mockSequenceFlowRecord = Mockito.mock(Record.class);
    final ProcessInstanceRecordValue mockSequenceFlowRecordValue =
        Mockito.mock(ProcessInstanceRecordValue.class);
    when(mockSequenceFlowRecord.getValue()).thenReturn(mockSequenceFlowRecordValue);

    when(mockSequenceFlowRecord.getIntent()).thenReturn(ProcessInstanceIntent.ELEMENT_ACTIVATING);
    when(mockSequenceFlowRecordValue.getBpmnElementType()).thenReturn(BpmnElementType.END_EVENT);
    assertThat(underTest.handlesRecord(mockSequenceFlowRecord)).isTrue();
  }

  @Test
  public void testGenerateIds() {
    final Record<ProcessInstanceRecordValue> mockSequenceFlowRecord = Mockito.mock(Record.class);
    when(mockSequenceFlowRecord.getKey()).thenReturn(123L);

    final var idList = underTest.generateIds(mockSequenceFlowRecord);

    assertThat(idList).isNotNull();
    assertThat(idList).containsExactly("123");
  }

  @Test
  public void testCreateNewEntity() {
    final var result = (underTest.createNewEntity("123"));
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("123");
  }

  @Test
  public void testGetIndexName() {
    final String expectedIndexName = "operate-flownodeinstance";
    when(mockFlowNodeInstanceTemplate.getFullQualifiedName()).thenReturn(expectedIndexName);

    assertThat(underTest.getIndexName()).isEqualTo(expectedIndexName);
    verify(mockFlowNodeInstanceTemplate, times(1)).getFullQualifiedName();
  }

  @Test
  public void testFlush() throws PersistenceException {
    final String expectedIndexName = "operate-flownodeinstance";
    when(mockFlowNodeInstanceTemplate.getFullQualifiedName()).thenReturn(expectedIndexName);

    final FlowNodeInstanceEntity inputEntity =
        new FlowNodeInstanceEntity()
            .setId("111")
            .setPartitionId(1)
            .setType(FlowNodeType.SERVICE_TASK)
            .setState(FlowNodeState.ACTIVE)
            .setTreePath(null)
            .setFlowNodeId("flowNode1")
            .setProcessDefinitionKey(222L)
            .setBpmnProcessId("bpmnId")
            .setLevel(0)
            .setStartDate(OffsetDateTime.now())
            .setEndDate(OffsetDateTime.now())
            .setPosition(333L);
    final NewElasticsearchBatchRequest mockRequest =
        Mockito.mock(NewElasticsearchBatchRequest.class);

    final Map<String, Object> expectedUpdateFields = new HashMap<>();
    expectedUpdateFields.put(FlowNodeInstanceTemplate.ID, inputEntity.getId());
    expectedUpdateFields.put(FlowNodeInstanceTemplate.PARTITION_ID, inputEntity.getPartitionId());
    expectedUpdateFields.put(FlowNodeInstanceTemplate.TYPE, inputEntity.getType());
    expectedUpdateFields.put(FlowNodeInstanceTemplate.STATE, inputEntity.getState());
    expectedUpdateFields.put(FlowNodeInstanceTemplate.TREE_PATH, inputEntity.getTreePath());
    expectedUpdateFields.put(FlowNodeInstanceTemplate.FLOW_NODE_ID, inputEntity.getFlowNodeId());
    expectedUpdateFields.put(
        FlowNodeInstanceTemplate.PROCESS_DEFINITION_KEY, inputEntity.getProcessDefinitionKey());
    expectedUpdateFields.put(
        FlowNodeInstanceTemplate.BPMN_PROCESS_ID, inputEntity.getBpmnProcessId());
    expectedUpdateFields.put(FlowNodeInstanceTemplate.LEVEL, inputEntity.getLevel());
    expectedUpdateFields.put(FlowNodeInstanceTemplate.START_DATE, inputEntity.getStartDate());
    expectedUpdateFields.put(FlowNodeInstanceTemplate.END_DATE, inputEntity.getEndDate());
    expectedUpdateFields.put(FlowNodeInstanceTemplate.POSITION, inputEntity.getPosition());

    underTest.flush(inputEntity, mockRequest);

    verify(mockRequest, times(1))
        .upsert(expectedIndexName, inputEntity.getId(), inputEntity, expectedUpdateFields);
    verify(mockFlowNodeInstanceTemplate, times(1)).getFullQualifiedName();
  }

  @Test
  public void testUpdateEntityForElementTerminated() {
    final Record<ProcessInstanceRecordValue> mockSequenceFlowRecord = Mockito.mock(Record.class);
    final ProcessInstanceRecordValue mockSequenceFlowRecordValue =
        Mockito.mock(ProcessInstanceRecordValue.class);
    when(mockSequenceFlowRecord.getValue()).thenReturn(mockSequenceFlowRecordValue);

    when(mockSequenceFlowRecord.getIntent()).thenReturn(ProcessInstanceIntent.ELEMENT_TERMINATED);
    when(mockSequenceFlowRecord.getKey()).thenReturn(111L);
    when(mockSequenceFlowRecord.getPartitionId()).thenReturn(1);
    when(mockSequenceFlowRecordValue.getElementId()).thenReturn("elementId");
    when(mockSequenceFlowRecordValue.getProcessInstanceKey()).thenReturn(222L);
    when(mockSequenceFlowRecordValue.getProcessDefinitionKey()).thenReturn(333L);
    when(mockSequenceFlowRecordValue.getBpmnProcessId()).thenReturn("bpmnProcessId");
    when(mockSequenceFlowRecordValue.getTenantId()).thenReturn("tenant1");
    when(mockSequenceFlowRecord.getTimestamp())
        .thenReturn(OffsetDateTime.now().toInstant().toEpochMilli());
    when(mockSequenceFlowRecordValue.getBpmnElementType()).thenReturn(BpmnElementType.SERVICE_TASK);

    final FlowNodeInstanceEntity flowNodeInstanceEntity = new FlowNodeInstanceEntity();
    underTest.updateEntity(mockSequenceFlowRecord, flowNodeInstanceEntity);

    assertThat(flowNodeInstanceEntity.getKey()).isEqualTo(111L);
    assertThat(flowNodeInstanceEntity.getId()).isEqualTo("111");
    assertThat(flowNodeInstanceEntity.getPartitionId()).isEqualTo(1);
    assertThat(flowNodeInstanceEntity.getFlowNodeId()).isEqualTo("elementId");
    assertThat(flowNodeInstanceEntity.getProcessInstanceKey()).isEqualTo(222L);
    assertThat(flowNodeInstanceEntity.getProcessDefinitionKey()).isEqualTo(333L);
    assertThat(flowNodeInstanceEntity.getBpmnProcessId()).isEqualTo("bpmnProcessId");
    assertThat(flowNodeInstanceEntity.getTenantId()).isEqualTo("tenant1");
    assertThat(flowNodeInstanceEntity.getTreePath()).isNull();
    assertThat(flowNodeInstanceEntity.getLevel()).isEqualTo(0);
    assertThat(flowNodeInstanceEntity.getState()).isEqualTo(FlowNodeState.TERMINATED);
    assertThat(flowNodeInstanceEntity.getEndDate()).isNotNull();
    assertThat(flowNodeInstanceEntity.getStartDate()).isNull();
    assertThat(flowNodeInstanceEntity.getPosition()).isNull();
  }

  @Test
  public void testUpdateEntityForElementCompleted() {
    final Record<ProcessInstanceRecordValue> mockSequenceFlowRecord = Mockito.mock(Record.class);
    final ProcessInstanceRecordValue mockSequenceFlowRecordValue =
        Mockito.mock(ProcessInstanceRecordValue.class);
    when(mockSequenceFlowRecord.getValue()).thenReturn(mockSequenceFlowRecordValue);

    when(mockSequenceFlowRecord.getIntent()).thenReturn(ProcessInstanceIntent.ELEMENT_COMPLETED);
    when(mockSequenceFlowRecord.getKey()).thenReturn(111L);
    when(mockSequenceFlowRecord.getPartitionId()).thenReturn(1);
    when(mockSequenceFlowRecordValue.getElementId()).thenReturn("elementId");
    when(mockSequenceFlowRecordValue.getProcessInstanceKey()).thenReturn(222L);
    when(mockSequenceFlowRecordValue.getProcessDefinitionKey()).thenReturn(333L);
    when(mockSequenceFlowRecordValue.getBpmnProcessId()).thenReturn("bpmnProcessId");
    when(mockSequenceFlowRecordValue.getTenantId()).thenReturn("tenant1");
    when(mockSequenceFlowRecord.getTimestamp())
        .thenReturn(OffsetDateTime.now().toInstant().toEpochMilli());
    when(mockSequenceFlowRecordValue.getBpmnElementType()).thenReturn(BpmnElementType.SERVICE_TASK);

    final FlowNodeInstanceEntity flowNodeInstanceEntity = new FlowNodeInstanceEntity();
    underTest.updateEntity(mockSequenceFlowRecord, flowNodeInstanceEntity);

    assertThat(flowNodeInstanceEntity.getKey()).isEqualTo(111L);
    assertThat(flowNodeInstanceEntity.getId()).isEqualTo("111");
    assertThat(flowNodeInstanceEntity.getPartitionId()).isEqualTo(1);
    assertThat(flowNodeInstanceEntity.getFlowNodeId()).isEqualTo("elementId");
    assertThat(flowNodeInstanceEntity.getProcessInstanceKey()).isEqualTo(222L);
    assertThat(flowNodeInstanceEntity.getProcessDefinitionKey()).isEqualTo(333L);
    assertThat(flowNodeInstanceEntity.getBpmnProcessId()).isEqualTo("bpmnProcessId");
    assertThat(flowNodeInstanceEntity.getTenantId()).isEqualTo("tenant1");
    assertThat(flowNodeInstanceEntity.getTreePath()).isNull();
    assertThat(flowNodeInstanceEntity.getLevel()).isEqualTo(0);
    assertThat(flowNodeInstanceEntity.getState()).isEqualTo(FlowNodeState.COMPLETED);
    assertThat(flowNodeInstanceEntity.getEndDate()).isNotNull();
    assertThat(flowNodeInstanceEntity.getStartDate()).isNull();
    assertThat(flowNodeInstanceEntity.getPosition()).isNull();
  }

  @Test
  public void testUpdateEntityForElementActivating() {
    final Record<ProcessInstanceRecordValue> mockSequenceFlowRecord = Mockito.mock(Record.class);
    final ProcessInstanceRecordValue mockSequenceFlowRecordValue =
        Mockito.mock(ProcessInstanceRecordValue.class);
    when(mockSequenceFlowRecord.getValue()).thenReturn(mockSequenceFlowRecordValue);

    when(mockSequenceFlowRecord.getIntent()).thenReturn(ProcessInstanceIntent.ELEMENT_ACTIVATING);
    when(mockSequenceFlowRecord.getKey()).thenReturn(111L);
    when(mockSequenceFlowRecord.getPartitionId()).thenReturn(1);
    when(mockSequenceFlowRecordValue.getElementId()).thenReturn("elementId");
    when(mockSequenceFlowRecordValue.getProcessInstanceKey()).thenReturn(222L);
    when(mockSequenceFlowRecordValue.getProcessDefinitionKey()).thenReturn(333L);
    when(mockSequenceFlowRecordValue.getBpmnProcessId()).thenReturn("bpmnProcessId");
    when(mockSequenceFlowRecordValue.getTenantId()).thenReturn("tenant1");
    when(mockSequenceFlowRecord.getTimestamp())
        .thenReturn(OffsetDateTime.now().toInstant().toEpochMilli());
    when(mockSequenceFlowRecordValue.getBpmnElementType()).thenReturn(BpmnElementType.SERVICE_TASK);
    when(mockSequenceFlowRecord.getPosition()).thenReturn(1L);

    final FlowNodeInstanceEntity flowNodeInstanceEntity = new FlowNodeInstanceEntity();
    underTest.updateEntity(mockSequenceFlowRecord, flowNodeInstanceEntity);

    assertThat(flowNodeInstanceEntity.getKey()).isEqualTo(111L);
    assertThat(flowNodeInstanceEntity.getId()).isEqualTo("111");
    assertThat(flowNodeInstanceEntity.getPartitionId()).isEqualTo(1);
    assertThat(flowNodeInstanceEntity.getFlowNodeId()).isEqualTo("elementId");
    assertThat(flowNodeInstanceEntity.getProcessInstanceKey()).isEqualTo(222L);
    assertThat(flowNodeInstanceEntity.getProcessDefinitionKey()).isEqualTo(333L);
    assertThat(flowNodeInstanceEntity.getBpmnProcessId()).isEqualTo("bpmnProcessId");
    assertThat(flowNodeInstanceEntity.getTenantId()).isEqualTo("tenant1");
    assertThat(flowNodeInstanceEntity.getTreePath()).isNull();
    assertThat(flowNodeInstanceEntity.getLevel()).isEqualTo(0);
    assertThat(flowNodeInstanceEntity.getState()).isEqualTo(FlowNodeState.ACTIVE);
    assertThat(flowNodeInstanceEntity.getEndDate()).isNull();
    assertThat(flowNodeInstanceEntity.getStartDate()).isNotNull();
    assertThat(flowNodeInstanceEntity.getPosition()).isEqualTo(1L);
  }

  @Test
  public void testUpdateEntityForElementActivated() {
    final Record<ProcessInstanceRecordValue> mockSequenceFlowRecord = Mockito.mock(Record.class);
    final ProcessInstanceRecordValue mockSequenceFlowRecordValue =
        Mockito.mock(ProcessInstanceRecordValue.class);
    when(mockSequenceFlowRecord.getValue()).thenReturn(mockSequenceFlowRecordValue);

    when(mockSequenceFlowRecord.getIntent()).thenReturn(ProcessInstanceIntent.ELEMENT_ACTIVATED);
    when(mockSequenceFlowRecord.getKey()).thenReturn(111L);
    when(mockSequenceFlowRecord.getPartitionId()).thenReturn(1);
    when(mockSequenceFlowRecordValue.getElementId()).thenReturn("elementId");
    when(mockSequenceFlowRecordValue.getProcessInstanceKey()).thenReturn(222L);
    when(mockSequenceFlowRecordValue.getProcessDefinitionKey()).thenReturn(333L);
    when(mockSequenceFlowRecordValue.getBpmnProcessId()).thenReturn("bpmnProcessId");
    when(mockSequenceFlowRecordValue.getTenantId()).thenReturn("tenant1");
    when(mockSequenceFlowRecordValue.getBpmnElementType()).thenReturn(BpmnElementType.SERVICE_TASK);

    final FlowNodeInstanceEntity flowNodeInstanceEntity = new FlowNodeInstanceEntity();
    underTest.updateEntity(mockSequenceFlowRecord, flowNodeInstanceEntity);

    assertThat(flowNodeInstanceEntity.getKey()).isEqualTo(111L);
    assertThat(flowNodeInstanceEntity.getId()).isEqualTo("111");
    assertThat(flowNodeInstanceEntity.getPartitionId()).isEqualTo(1);
    assertThat(flowNodeInstanceEntity.getFlowNodeId()).isEqualTo("elementId");
    assertThat(flowNodeInstanceEntity.getProcessInstanceKey()).isEqualTo(222L);
    assertThat(flowNodeInstanceEntity.getProcessDefinitionKey()).isEqualTo(333L);
    assertThat(flowNodeInstanceEntity.getBpmnProcessId()).isEqualTo("bpmnProcessId");
    assertThat(flowNodeInstanceEntity.getTenantId()).isEqualTo("tenant1");
    assertThat(flowNodeInstanceEntity.getTreePath()).isNull();
    assertThat(flowNodeInstanceEntity.getLevel()).isEqualTo(0);
    assertThat(flowNodeInstanceEntity.getState()).isEqualTo(FlowNodeState.ACTIVE);
    assertThat(flowNodeInstanceEntity.getEndDate()).isNull();
    assertThat(flowNodeInstanceEntity.getStartDate()).isNull();
    assertThat(flowNodeInstanceEntity.getPosition()).isNull();
  }
}
