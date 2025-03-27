/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.exporter.handlers.EventFromProcessInstanceHandler.ID_PATTERN;
import static io.camunda.exporter.handlers.EventFromProcessInstanceHandler.PROCESS_INSTANCE_STATES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.template.EventTemplate;
import io.camunda.webapps.schema.entities.event.EventEntity;
import io.camunda.webapps.schema.entities.event.EventSourceType;
import io.camunda.webapps.schema.entities.event.EventType;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

final class EventFromProcessInstanceHandlerTest {
  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = EventTemplate.INDEX_NAME;

  private final EventFromProcessInstanceHandler underTest =
      new EventFromProcessInstanceHandler(indexName);

  @Test
  void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.PROCESS_INSTANCE);
  }

  @Test
  void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(EventEntity.class);
  }

  @Test
  void shouldHandleRecord() {
    PROCESS_INSTANCE_STATES.stream()
        .map(this::generateRecord)
        .forEach(this::assertShouldHandleRecord);
  }

  @Test
  void shouldNotHandleRecord() {
    Stream.of(ProcessInstanceIntent.values())
        .filter(intent -> !PROCESS_INSTANCE_STATES.contains(intent))
        .map(this::generateRecord)
        .forEach(this::assertShouldNotHandleRecord);
  }

  @Test
  void testGenerateIds() {
    // given
    final int key = 456;
    final int processInstanceKey = 123;
    final var recordValue =
        ImmutableProcessInstanceRecordValue.builder()
            .withProcessInstanceKey(processInstanceKey)
            .build();
    final Record<ProcessInstanceRecordValue> record =
        factory.generateRecord(
            ValueType.PROCESS_INSTANCE, r -> r.withKey(key).withValue(recordValue));

    // when
    final var ids = underTest.generateIds(record);

    // then
    assertThat(ids).containsExactly(String.format(ID_PATTERN, processInstanceKey, key));
  }

  @Test
  void testCreateNewEntity() {
    // given
    final String id = "id";

    // when
    final var entity = underTest.createNewEntity(id);

    // then
    assertThat(entity).isNotNull();
    assertThat(entity.getId()).isEqualTo(id);
  }

  @Test
  void testUpdateEntity() {
    // given
    final long position = 222;
    final int partitionId = 10;
    final int processInstanceKey = 123;
    final long recordKey = 456;
    final String elementId = "elementId";
    final String bpmnProcessId = "bpmnProcessId";
    final String tenantId = "tenantId";
    final var recordValue =
        ImmutableProcessInstanceRecordValue.builder()
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(elementId)
            .withBpmnProcessId(bpmnProcessId)
            .withTenantId(tenantId)
            .build();
    final Record<ProcessInstanceRecordValue> record =
        factory.generateRecord(
            ValueType.PROCESS_INSTANCE,
            r ->
                r.withPosition(position)
                    .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
                    .withKey(recordKey)
                    .withPartitionId(partitionId)
                    .withValueType(ValueType.PROCESS_INSTANCE)
                    .withValue(recordValue));

    // when
    final var entity = new EventEntity();
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getId()).isEqualTo(String.format(ID_PATTERN, processInstanceKey, recordKey));
    assertThat(entity.getKey()).isEqualTo(recordKey);
    assertThat(entity.getPartitionId()).isEqualTo(partitionId);
    assertThat(entity.getEventSourceType()).isEqualTo(EventSourceType.PROCESS_INSTANCE);
    assertThat(entity.getEventType()).isEqualTo(EventType.ELEMENT_ACTIVATING);
    assertThat(entity.getProcessInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(entity.getFlowNodeInstanceKey()).isEqualTo(recordKey);
    assertThat(entity.getFlowNodeId()).isEqualTo(elementId);
    assertThat(entity.getBpmnProcessId()).isEqualTo(bpmnProcessId);
    assertThat(entity.getTenantId()).isEqualTo(tenantId);
    assertThat(entity.getPosition()).isEqualTo(position);
  }

  @Test
  void shouldAddEntityOnFlush() {
    // given
    final String expectedIndexName = EventTemplate.INDEX_NAME;
    final String id = "555";
    final long position = 456L;
    final long key = 333L;
    final EventEntity inputEntity = new EventEntity().setId(id).setKey(key).setPosition(position);

    final Map<String, Object> expectedUpdateFields = new LinkedHashMap<>();
    expectedUpdateFields.put("key", key);
    expectedUpdateFields.put("position", position);
    expectedUpdateFields.put("dateTime", null);
    expectedUpdateFields.put("flowNodeId", null);
    expectedUpdateFields.put("eventSourceType", null);
    expectedUpdateFields.put("eventType", null);
    expectedUpdateFields.put("bpmnProcessId", null);
    expectedUpdateFields.put("processDefinitionKey", null);

    final BatchRequest mockRequest = Mockito.mock(BatchRequest.class);

    // when
    underTest.flush(inputEntity, mockRequest);

    // then
    verify(mockRequest, times(1)).upsert(expectedIndexName, id, inputEntity, expectedUpdateFields);
  }

  private Record<ProcessInstanceRecordValue> generateRecord(final Intent intent) {
    return factory.generateRecord(ValueType.PROCESS_INSTANCE, r -> r.withIntent(intent));
  }

  private void assertShouldHandleRecord(final Record<ProcessInstanceRecordValue> record) {
    assertThat(underTest.handlesRecord(record)).isTrue();
  }

  private void assertShouldNotHandleRecord(final Record<ProcessInstanceRecordValue> record) {
    assertThat(underTest.handlesRecord(record)).isFalse();
  }
}
