/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.operate.exporter.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;

import io.camunda.operate.entities.EventEntity;
import io.camunda.operate.entities.EventSourceType;
import io.camunda.operate.entities.EventType;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.schema.templates.EventTemplate;
import io.camunda.operate.store.elasticsearch.NewElasticsearchBatchRequest;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class EventFromProcessInstanceHandlerTest {

  private EventFromProcessInstanceHandler underTest;

  @Mock private EventTemplate mockEventTemplate;

  @BeforeEach
  public void setup() {
    underTest = new EventFromProcessInstanceHandler(mockEventTemplate, false);
  }

  @Test
  public void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.PROCESS_INSTANCE);
  }

  @Test
  public void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(EventEntity.class);
  }

  @Test
  public void testHandlesRecord() {
    final Record<ProcessInstanceRecordValue> mockProcessInstanceRecord = Mockito.mock(Record.class);
    when(mockProcessInstanceRecord.getIntent()).thenReturn(ProcessInstanceIntent.ELEMENT_ACTIVATED);
    assertThat(underTest.handlesRecord(mockProcessInstanceRecord)).isTrue();
  }

  @Test
  public void testGenerateIds() {
    final Record<ProcessInstanceRecordValue> mockProcessInstanceRecord = Mockito.mock(Record.class);
    final ProcessInstanceRecordValue mockProcessInstanceRecordValue =
        Mockito.mock(ProcessInstanceRecordValue.class);

    when(mockProcessInstanceRecord.getValue()).thenReturn(mockProcessInstanceRecordValue);
    when(mockProcessInstanceRecord.getKey()).thenReturn(55L);
    when(mockProcessInstanceRecordValue.getProcessInstanceKey()).thenReturn(123L);

    final String expectedId = "123_55";

    final var idList = underTest.generateIds(mockProcessInstanceRecord);

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
    final String expectedIndexName = "event";
    when(mockEventTemplate.getFullQualifiedName()).thenReturn(expectedIndexName);

    final EventEntity inputEntity = new EventEntity().setId("555").setKey(333L).setPosition(456L);
    final NewElasticsearchBatchRequest mockRequest =
        Mockito.mock(NewElasticsearchBatchRequest.class);
    final Map<String, Object> expectedUpdateFields = new LinkedHashMap<>();
    expectedUpdateFields.put("dateTime", null);
    expectedUpdateFields.put("position", 456L);
    expectedUpdateFields.put("flowNodeId", null);
    expectedUpdateFields.put("eventSourceType", null);
    expectedUpdateFields.put("eventType", null);
    expectedUpdateFields.put("bpmnProcessId", null);
    expectedUpdateFields.put("key", 333L);
    expectedUpdateFields.put("processDefinitionKey", null);

    underTest.flush(inputEntity, mockRequest);

    verify(mockRequest, times(1))
        .upsert(expectedIndexName, "555", inputEntity, expectedUpdateFields);
    verify(mockEventTemplate, times(1)).getFullQualifiedName();
  }

  @Test
  public void testGetIndexName() {
    final String expectedIndexName = "event";
    when(mockEventTemplate.getFullQualifiedName()).thenReturn(expectedIndexName);

    assertThat(underTest.getIndexName()).isEqualTo(expectedIndexName);
    verify(mockEventTemplate, times(1)).getFullQualifiedName();
  }

  @Test
  public void testUpdateEntity() {

    final Record<ProcessInstanceRecordValue> mockProcessInstanceRecord = Mockito.mock(Record.class);
    final ProcessInstanceRecordValue mockProcessInstanceRecordValue =
        Mockito.mock(ProcessInstanceRecordValue.class);

    when(mockProcessInstanceRecord.getValue()).thenReturn(mockProcessInstanceRecordValue);
    when(mockProcessInstanceRecord.getIntent()).thenReturn(ProcessInstanceIntent.ELEMENT_ACTIVATED);
    when(mockProcessInstanceRecord.getKey()).thenReturn(25L);
    when(mockProcessInstanceRecord.getPartitionId()).thenReturn(10);
    when(mockProcessInstanceRecord.getValueType()).thenReturn(ValueType.PROCESS_INSTANCE);
    when(mockProcessInstanceRecordValue.getProcessInstanceKey()).thenReturn(123L);
    when(mockProcessInstanceRecordValue.getElementId()).thenReturn("elementId");
    when(mockProcessInstanceRecordValue.getBpmnProcessId()).thenReturn("bpmnProcessId");
    when(mockProcessInstanceRecordValue.getTenantId()).thenReturn("tenantId");

    final EventEntity eventEntity = new EventEntity();
    underTest.updateEntity(mockProcessInstanceRecord, eventEntity);

    assertThat(eventEntity.getId()).isEqualTo("123_25");
    assertThat(eventEntity.getKey()).isEqualTo(25L);
    assertThat(eventEntity.getPartitionId()).isEqualTo(10);
    assertThat(eventEntity.getEventSourceType()).isEqualTo(EventSourceType.PROCESS_INSTANCE);
    assertThat(eventEntity.getEventType()).isEqualTo(EventType.ELEMENT_ACTIVATED);
    assertThat(eventEntity.getProcessInstanceKey()).isEqualTo(123L);
    assertThat(eventEntity.getBpmnProcessId()).isEqualTo("bpmnProcessId");
    assertThat(eventEntity.getFlowNodeId()).isEqualTo("elementId");
    assertThat(eventEntity.getFlowNodeInstanceKey()).isEqualTo(25L);
    assertThat(eventEntity.getTenantId()).isEqualTo("tenantId");
  }
}
