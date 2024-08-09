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
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.templates.FlowNodeInstanceTemplate;
import io.camunda.operate.store.elasticsearch.NewElasticsearchBatchRequest;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class FlowNodeInstanceIncidentHandlerTest {

  private FlowNodeInstanceIncidentHandler underTest;

  @Mock private FlowNodeInstanceTemplate mockFlowNodeInstanceTemplate;

  @BeforeEach
  public void setup() {
    underTest = new FlowNodeInstanceIncidentHandler(mockFlowNodeInstanceTemplate);
  }

  @Test
  public void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.INCIDENT);
  }

  @Test
  public void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(FlowNodeInstanceEntity.class);
  }

  @Test
  public void testHandlesRecord() {
    final Record<IncidentRecordValue> mockIncidentRecord = Mockito.mock(Record.class);
    assertThat(underTest.handlesRecord(mockIncidentRecord)).isTrue();
  }

  @Test
  public void testGenerateIds() {
    final Record<IncidentRecordValue> mockIncidentRecord = Mockito.mock(Record.class);
    final IncidentRecordValue mockIncidentRecordValue = Mockito.mock(IncidentRecordValue.class);

    when(mockIncidentRecord.getValue()).thenReturn(mockIncidentRecordValue);
    when(mockIncidentRecordValue.getElementInstanceKey()).thenReturn(123L);

    final String expectedId = String.valueOf(123L);

    final var idList = underTest.generateIds(mockIncidentRecord);

    assertThat(idList).isNotNull();
    assertThat(idList).containsExactly(expectedId);
  }

  @Test
  public void testCreateNewEntity() {
    final var result = (underTest.createNewEntity("id"));
    assertThat(result).isNotNull();
    assertThat(result.getId()).isEqualTo("id");
  }

  @Test
  public void testFlush() throws PersistenceException {
    final String expectedIndexName = "operate-flownodeinstance";
    when(mockFlowNodeInstanceTemplate.getFullQualifiedName()).thenReturn(expectedIndexName);

    final FlowNodeInstanceEntity inputEntity =
        new FlowNodeInstanceEntity().setIncidentKey(123L).setId("id");
    final NewElasticsearchBatchRequest mockRequest =
        Mockito.mock(NewElasticsearchBatchRequest.class);

    final Map<String, Object> expectedUpdateFields = new HashMap<>();
    expectedUpdateFields.put(FlowNodeInstanceTemplate.INCIDENT_KEY, inputEntity.getIncidentKey());

    underTest.flush(inputEntity, mockRequest);

    verify(mockRequest, times(1))
        .upsert(expectedIndexName, inputEntity.getId(), inputEntity, expectedUpdateFields);
    verify(mockFlowNodeInstanceTemplate, times(1)).getFullQualifiedName();
  }

  @Test
  public void testGetIndexName() {
    final String expectedIndexName = "operate-flownodeinstance";
    when(mockFlowNodeInstanceTemplate.getFullQualifiedName()).thenReturn(expectedIndexName);

    assertThat(underTest.getIndexName()).isEqualTo(expectedIndexName);
    verify(mockFlowNodeInstanceTemplate, times(1)).getFullQualifiedName();
  }

  @Test
  public void testUpdateEntityWithCreatedIncident() {
    final Record<IncidentRecordValue> mockIncidentRecord = Mockito.mock(Record.class);
    final IncidentRecordValue mockIncidentRecordValue = Mockito.mock(IncidentRecordValue.class);
    when(mockIncidentRecord.getValue()).thenReturn(mockIncidentRecordValue);

    when(mockIncidentRecordValue.getElementInstanceKey()).thenReturn(111L);
    when(mockIncidentRecord.getPartitionId()).thenReturn(1);
    when(mockIncidentRecordValue.getElementId()).thenReturn("elementId");
    when(mockIncidentRecordValue.getProcessInstanceKey()).thenReturn(222L);
    when(mockIncidentRecordValue.getProcessDefinitionKey()).thenReturn(333L);
    when(mockIncidentRecordValue.getBpmnProcessId()).thenReturn("bpmnId");
    when(mockIncidentRecordValue.getTenantId()).thenReturn("tenant1");

    when(mockIncidentRecord.getIntent()).thenReturn(IncidentIntent.CREATED);
    when(mockIncidentRecord.getKey()).thenReturn(444L);

    final FlowNodeInstanceEntity flowNodeInstanceEntity = new FlowNodeInstanceEntity();
    underTest.updateEntity(mockIncidentRecord, flowNodeInstanceEntity);

    assertThat(flowNodeInstanceEntity.getId()).isEqualTo("111");
    assertThat(flowNodeInstanceEntity.getKey()).isEqualTo(111L);
    assertThat(flowNodeInstanceEntity.getFlowNodeId()).isEqualTo("elementId");
    assertThat(flowNodeInstanceEntity.getProcessInstanceKey()).isEqualTo(222L);
    assertThat(flowNodeInstanceEntity.getProcessDefinitionKey()).isEqualTo(333L);
    assertThat(flowNodeInstanceEntity.getBpmnProcessId()).isEqualTo("bpmnId");
    assertThat(flowNodeInstanceEntity.getTenantId()).isEqualTo("tenant1");
    assertThat(flowNodeInstanceEntity.getIncidentKey()).isEqualTo(444L);
  }

  @Test
  public void testUpdateEntityWithResolvedIncident() {
    final Record<IncidentRecordValue> mockIncidentRecord = Mockito.mock(Record.class);
    final IncidentRecordValue mockIncidentRecordValue = Mockito.mock(IncidentRecordValue.class);
    when(mockIncidentRecord.getValue()).thenReturn(mockIncidentRecordValue);

    when(mockIncidentRecordValue.getElementInstanceKey()).thenReturn(111L);
    when(mockIncidentRecord.getPartitionId()).thenReturn(1);
    when(mockIncidentRecordValue.getElementId()).thenReturn("elementId");
    when(mockIncidentRecordValue.getProcessInstanceKey()).thenReturn(222L);
    when(mockIncidentRecordValue.getProcessDefinitionKey()).thenReturn(333L);
    when(mockIncidentRecordValue.getBpmnProcessId()).thenReturn("bpmnId");
    when(mockIncidentRecordValue.getTenantId()).thenReturn("tenant1");

    when(mockIncidentRecord.getIntent()).thenReturn(IncidentIntent.RESOLVED);

    final FlowNodeInstanceEntity flowNodeInstanceEntity = new FlowNodeInstanceEntity();
    underTest.updateEntity(mockIncidentRecord, flowNodeInstanceEntity);

    assertThat(flowNodeInstanceEntity.getId()).isEqualTo("111");
    assertThat(flowNodeInstanceEntity.getKey()).isEqualTo(111L);
    assertThat(flowNodeInstanceEntity.getFlowNodeId()).isEqualTo("elementId");
    assertThat(flowNodeInstanceEntity.getProcessInstanceKey()).isEqualTo(222L);
    assertThat(flowNodeInstanceEntity.getProcessDefinitionKey()).isEqualTo(333L);
    assertThat(flowNodeInstanceEntity.getBpmnProcessId()).isEqualTo("bpmnId");
    assertThat(flowNodeInstanceEntity.getTenantId()).isEqualTo("tenant1");
    assertThat(flowNodeInstanceEntity.getIncidentKey()).isNull();
  }

  @Test
  public void testUpdateEntityWithOtherIncident() {
    final Record<IncidentRecordValue> mockIncidentRecord = Mockito.mock(Record.class);
    final IncidentRecordValue mockIncidentRecordValue = Mockito.mock(IncidentRecordValue.class);
    when(mockIncidentRecord.getValue()).thenReturn(mockIncidentRecordValue);

    when(mockIncidentRecordValue.getElementInstanceKey()).thenReturn(111L);
    when(mockIncidentRecord.getPartitionId()).thenReturn(1);
    when(mockIncidentRecordValue.getElementId()).thenReturn("elementId");
    when(mockIncidentRecordValue.getProcessInstanceKey()).thenReturn(222L);
    when(mockIncidentRecordValue.getProcessDefinitionKey()).thenReturn(333L);
    when(mockIncidentRecordValue.getBpmnProcessId()).thenReturn("bpmnId");
    when(mockIncidentRecordValue.getTenantId()).thenReturn("tenant1");

    when(mockIncidentRecord.getIntent()).thenReturn(IncidentIntent.MIGRATED);

    final FlowNodeInstanceEntity flowNodeInstanceEntity = new FlowNodeInstanceEntity();
    underTest.updateEntity(mockIncidentRecord, flowNodeInstanceEntity);

    assertThat(flowNodeInstanceEntity.getId()).isEqualTo("111");
    assertThat(flowNodeInstanceEntity.getKey()).isEqualTo(111L);
    assertThat(flowNodeInstanceEntity.getFlowNodeId()).isEqualTo("elementId");
    assertThat(flowNodeInstanceEntity.getProcessInstanceKey()).isEqualTo(222L);
    assertThat(flowNodeInstanceEntity.getProcessDefinitionKey()).isEqualTo(333L);
    assertThat(flowNodeInstanceEntity.getBpmnProcessId()).isEqualTo("bpmnId");
    assertThat(flowNodeInstanceEntity.getTenantId()).isEqualTo("tenant1");
    assertThat(flowNodeInstanceEntity.getIncidentKey()).isNull();
  }
}
