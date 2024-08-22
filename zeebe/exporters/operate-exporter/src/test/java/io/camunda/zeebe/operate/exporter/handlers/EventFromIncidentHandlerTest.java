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
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class EventFromIncidentHandlerTest {

  private EventFromIncidentHandler underTest;

  @Mock private EventTemplate mockEventTemplate;

  @BeforeEach
  public void setup() {
    underTest = new EventFromIncidentHandler(mockEventTemplate, false);
  }

  @Test
  public void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.INCIDENT);
  }

  @Test
  public void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(EventEntity.class);
  }

  @Test
  public void testHandlesRecord() {
    final Record<IncidentRecordValue> mockIncidentRecord = Mockito.mock(Record.class);
    when(mockIncidentRecord.getIntent()).thenReturn(IncidentIntent.CREATED);
    assertThat(underTest.handlesRecord(mockIncidentRecord)).isTrue();
  }

  @Test
  public void testGenerateIds() {
    final Record<IncidentRecordValue> mockIncidentRecord = Mockito.mock(Record.class);
    final IncidentRecordValue mockIncidentRecordValue = Mockito.mock(IncidentRecordValue.class);

    when(mockIncidentRecord.getValue()).thenReturn(mockIncidentRecordValue);
    when(mockIncidentRecordValue.getProcessInstanceKey()).thenReturn(123L);
    when(mockIncidentRecordValue.getElementInstanceKey()).thenReturn(55L);

    final String expectedId = "123_55";

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
    final String expectedIndexName = "event";
    when(mockEventTemplate.getFullQualifiedName()).thenReturn(expectedIndexName);

    final EventEntity inputEntity =
        new EventEntity().setId("555").setKey(333L).setPositionIncident(456L);
    final NewElasticsearchBatchRequest mockRequest =
        Mockito.mock(NewElasticsearchBatchRequest.class);
    final Map<String, Object> expectedUpdateFields = new LinkedHashMap<>();
    expectedUpdateFields.put("dateTime", null);
    expectedUpdateFields.put("positionIncident", 456L);
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

    final Record<IncidentRecordValue> mockIncidentRecord = Mockito.mock(Record.class);
    final IncidentRecordValue mockIncidentRecordValue = Mockito.mock(IncidentRecordValue.class);

    when(mockIncidentRecord.getValue()).thenReturn(mockIncidentRecordValue);
    when(mockIncidentRecord.getIntent()).thenReturn(IncidentIntent.CREATED);
    when(mockIncidentRecord.getKey()).thenReturn(25L);
    when(mockIncidentRecord.getPartitionId()).thenReturn(10);
    when(mockIncidentRecord.getValueType()).thenReturn(ValueType.INCIDENT);
    when(mockIncidentRecordValue.getProcessInstanceKey()).thenReturn(123L);
    when(mockIncidentRecordValue.getElementInstanceKey()).thenReturn(66L);
    when(mockIncidentRecordValue.getElementId()).thenReturn("elementId");
    when(mockIncidentRecordValue.getBpmnProcessId()).thenReturn("bpmnProcessId");
    when(mockIncidentRecordValue.getErrorMessage()).thenReturn("error");
    when(mockIncidentRecordValue.getTenantId()).thenReturn("tenantId");

    final EventEntity eventEntity = new EventEntity();
    underTest.updateEntity(mockIncidentRecord, eventEntity);

    assertThat(eventEntity.getId()).isEqualTo("123_66");
    assertThat(eventEntity.getKey()).isEqualTo(25L);
    assertThat(eventEntity.getPartitionId()).isEqualTo(10);
    assertThat(eventEntity.getEventSourceType()).isEqualTo(EventSourceType.INCIDENT);
    assertThat(eventEntity.getEventType()).isEqualTo(EventType.CREATED);
    assertThat(eventEntity.getProcessInstanceKey()).isEqualTo(123L);
    assertThat(eventEntity.getBpmnProcessId()).isEqualTo("bpmnProcessId");
    assertThat(eventEntity.getFlowNodeId()).isEqualTo("elementId");
    assertThat(eventEntity.getFlowNodeInstanceKey()).isEqualTo(66L);
    assertThat(eventEntity.getMetadata().getIncidentErrorMessage()).isEqualTo("error");
    assertThat(eventEntity.getTenantId()).isEqualTo("tenantId");
  }
}
