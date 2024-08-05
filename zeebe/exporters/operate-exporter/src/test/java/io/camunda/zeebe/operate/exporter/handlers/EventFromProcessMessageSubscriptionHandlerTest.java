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
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.ProcessMessageSubscriptionRecordValue;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class EventFromProcessMessageSubscriptionHandlerTest {

  private EventFromProcessMessageSubscriptionHandler underTest;

  @Mock private EventTemplate mockEventTemplate;

  @BeforeEach
  public void setup() {
    underTest = new EventFromProcessMessageSubscriptionHandler(mockEventTemplate, false);
  }

  @Test
  public void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.PROCESS_MESSAGE_SUBSCRIPTION);
  }

  @Test
  public void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(EventEntity.class);
  }

  @Test
  public void testHandlesRecord() {
    final Record<ProcessMessageSubscriptionRecordValue> mockProcessMessageSubscriptionRecord =
        Mockito.mock(Record.class);
    when(mockProcessMessageSubscriptionRecord.getIntent())
        .thenReturn(ProcessMessageSubscriptionIntent.CREATED);
    assertThat(underTest.handlesRecord(mockProcessMessageSubscriptionRecord)).isTrue();
  }

  @Test
  public void testGenerateIds() {
    final Record<ProcessMessageSubscriptionRecordValue> mockProcessMessageSubscriptionRecord =
        Mockito.mock(Record.class);
    final ProcessMessageSubscriptionRecordValue mockProcessMessageSubscriptionRecordValue =
        Mockito.mock(ProcessMessageSubscriptionRecordValue.class);

    when(mockProcessMessageSubscriptionRecord.getValue())
        .thenReturn(mockProcessMessageSubscriptionRecordValue);
    when(mockProcessMessageSubscriptionRecordValue.getProcessInstanceKey()).thenReturn(123L);
    when(mockProcessMessageSubscriptionRecordValue.getElementInstanceKey()).thenReturn(55L);

    final String expectedId = "123_55";

    final var idList = underTest.generateIds(mockProcessMessageSubscriptionRecord);

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
        new EventEntity().setId("555").setKey(333L).setPositionProcessMessageSubscription(456L);
    final NewElasticsearchBatchRequest mockRequest =
        Mockito.mock(NewElasticsearchBatchRequest.class);
    final Map<String, Object> expectedUpdateFields = new LinkedHashMap<>();
    expectedUpdateFields.put("dateTime", null);
    expectedUpdateFields.put("positionProcessMessageSubscription", 456L);
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

    final Record<ProcessMessageSubscriptionRecordValue> mockProcessMessageSubscriptionRecord =
        Mockito.mock(Record.class);
    final ProcessMessageSubscriptionRecordValue mockProcessMessageSubscriptionRecordValue =
        Mockito.mock(ProcessMessageSubscriptionRecordValue.class);

    when(mockProcessMessageSubscriptionRecord.getValue())
        .thenReturn(mockProcessMessageSubscriptionRecordValue);
    when(mockProcessMessageSubscriptionRecord.getIntent())
        .thenReturn(ProcessMessageSubscriptionIntent.CREATED);
    when(mockProcessMessageSubscriptionRecord.getPosition()).thenReturn(33L);
    when(mockProcessMessageSubscriptionRecord.getPartitionId()).thenReturn(10);
    when(mockProcessMessageSubscriptionRecord.getKey()).thenReturn(16L);
    when(mockProcessMessageSubscriptionRecord.getValueType())
        .thenReturn(ValueType.PROCESS_MESSAGE_SUBSCRIPTION);
    when(mockProcessMessageSubscriptionRecordValue.getProcessInstanceKey()).thenReturn(123L);
    when(mockProcessMessageSubscriptionRecordValue.getElementInstanceKey()).thenReturn(25L);
    when(mockProcessMessageSubscriptionRecordValue.getElementId()).thenReturn("elementId");
    when(mockProcessMessageSubscriptionRecordValue.getBpmnProcessId()).thenReturn("bpmnProcessId");
    when(mockProcessMessageSubscriptionRecordValue.getTenantId()).thenReturn("tenantId");
    when(mockProcessMessageSubscriptionRecordValue.getMessageName()).thenReturn("message");
    when(mockProcessMessageSubscriptionRecordValue.getCorrelationKey()).thenReturn("correlation");

    final EventEntity eventEntity = new EventEntity();
    underTest.updateEntity(mockProcessMessageSubscriptionRecord, eventEntity);

    assertThat(eventEntity.getId()).isEqualTo("123_25");
    assertThat(eventEntity.getKey()).isEqualTo(16L);
    assertThat(eventEntity.getPartitionId()).isEqualTo(10);
    assertThat(eventEntity.getEventSourceType())
        .isEqualTo(EventSourceType.PROCESS_MESSAGE_SUBSCRIPTION);
    assertThat(eventEntity.getEventType()).isEqualTo(EventType.CREATED);
    assertThat(eventEntity.getProcessInstanceKey()).isEqualTo(123L);
    assertThat(eventEntity.getBpmnProcessId()).isEqualTo("bpmnProcessId");
    assertThat(eventEntity.getFlowNodeId()).isEqualTo("elementId");
    assertThat(eventEntity.getFlowNodeInstanceKey()).isEqualTo(25L);
    assertThat(eventEntity.getPositionProcessMessageSubscription()).isEqualTo(33L);
    assertThat(eventEntity.getTenantId()).isEqualTo("tenantId");
    assertThat(eventEntity.getMetadata().getMessageName()).isEqualTo("message");
    assertThat(eventEntity.getMetadata().getCorrelationKey()).isEqualTo("correlation");
  }
}
