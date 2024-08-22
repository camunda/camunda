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
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class EventFromJobHandlerTest {

  private EventFromJobHandler underTest;

  @Mock private EventTemplate mockEventTemplate;

  @BeforeEach
  public void setup() {
    underTest = new EventFromJobHandler(mockEventTemplate, false);
  }

  @Test
  public void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.JOB);
  }

  @Test
  public void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(EventEntity.class);
  }

  @Test
  public void testHandlesRecord() {
    final Record<JobRecordValue> mockJobRecord = Mockito.mock(Record.class);
    when(mockJobRecord.getIntent()).thenReturn(JobIntent.CREATED);
    assertThat(underTest.handlesRecord(mockJobRecord)).isTrue();
  }

  @Test
  public void testGenerateIds() {
    final Record<JobRecordValue> mockJobRecord = Mockito.mock(Record.class);
    final JobRecordValue mockJobRecordValue = Mockito.mock(JobRecordValue.class);

    when(mockJobRecord.getValue()).thenReturn(mockJobRecordValue);
    when(mockJobRecordValue.getProcessInstanceKey()).thenReturn(123L);
    when(mockJobRecordValue.getElementInstanceKey()).thenReturn(55L);

    final String expectedId = "123_55";

    final var idList = underTest.generateIds(mockJobRecord);

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
        new EventEntity().setId("555").setKey(333L).setPositionJob(456L);
    final NewElasticsearchBatchRequest mockRequest =
        Mockito.mock(NewElasticsearchBatchRequest.class);
    final Map<String, Object> expectedUpdateFields = new LinkedHashMap<>();
    expectedUpdateFields.put("dateTime", null);
    expectedUpdateFields.put("positionJob", 456L);
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

    final Record<JobRecordValue> mockJobRecord = Mockito.mock(Record.class);
    final JobRecordValue mockJobRecordValue = Mockito.mock(JobRecordValue.class);

    when(mockJobRecord.getValue()).thenReturn(mockJobRecordValue);
    when(mockJobRecord.getIntent()).thenReturn(JobIntent.CREATED);
    when(mockJobRecord.getKey()).thenReturn(25L);
    when(mockJobRecord.getPartitionId()).thenReturn(10);
    when(mockJobRecord.getValueType()).thenReturn(ValueType.JOB);
    when(mockJobRecordValue.getProcessInstanceKey()).thenReturn(123L);
    when(mockJobRecordValue.getElementInstanceKey()).thenReturn(66L);
    when(mockJobRecordValue.getElementId()).thenReturn("elementId");
    when(mockJobRecordValue.getBpmnProcessId()).thenReturn("bpmnProcessId");
    when(mockJobRecordValue.getType()).thenReturn("jobType");
    when(mockJobRecordValue.getRetries()).thenReturn(3);
    when(mockJobRecordValue.getWorker()).thenReturn("jobWorker");
    when(mockJobRecordValue.getCustomHeaders()).thenReturn(Map.of("key", "val"));
    when(mockJobRecordValue.getTenantId()).thenReturn("tenantId");

    final EventEntity eventEntity = new EventEntity();
    underTest.updateEntity(mockJobRecord, eventEntity);

    assertThat(eventEntity.getId()).isEqualTo("123_66");
    assertThat(eventEntity.getKey()).isEqualTo(25L);
    assertThat(eventEntity.getPartitionId()).isEqualTo(10);
    assertThat(eventEntity.getEventSourceType()).isEqualTo(EventSourceType.JOB);
    assertThat(eventEntity.getEventType()).isEqualTo(EventType.CREATED);
    assertThat(eventEntity.getProcessInstanceKey()).isEqualTo(123L);
    assertThat(eventEntity.getBpmnProcessId()).isEqualTo("bpmnProcessId");
    assertThat(eventEntity.getFlowNodeId()).isEqualTo("elementId");
    assertThat(eventEntity.getFlowNodeInstanceKey()).isEqualTo(66L);
    assertThat(eventEntity.getMetadata().getJobType()).isEqualTo("jobType");
    assertThat(eventEntity.getMetadata().getJobRetries()).isEqualTo(3);
    assertThat(eventEntity.getMetadata().getJobWorker()).isEqualTo("jobWorker");
    assertThat(eventEntity.getMetadata().getJobCustomHeaders()).isEqualTo(Map.of("key", "val"));
    assertThat(eventEntity.getMetadata().getJobKey()).isEqualTo(25L);
    assertThat(eventEntity.getTenantId()).isEqualTo("tenantId");
  }
}
