/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.exporter.handlers.EventFromIncidentHandler.ID_PATTERN;
import static io.camunda.webapps.schema.descriptors.template.EventTemplate.CORRELATION_KEY;
import static io.camunda.webapps.schema.descriptors.template.EventTemplate.MESSAGE_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.template.EventTemplate;
import io.camunda.webapps.schema.entities.event.EventEntity;
import io.camunda.webapps.schema.entities.event.EventMetadataEntity;
import io.camunda.webapps.schema.entities.event.EventSourceType;
import io.camunda.webapps.schema.entities.event.EventType;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessMessageSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessMessageSubscriptionRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.mockito.Mockito;

final class EventFromProcessMessageSubscriptionHandlerTest {
  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = EventTemplate.INDEX_NAME;

  private final EventFromProcessMessageSubscriptionHandler underTest =
      new EventFromProcessMessageSubscriptionHandler(indexName);

  @Test
  void testGetHandledValueType() {
    assertThat(underTest.getHandledValueType()).isEqualTo(ValueType.PROCESS_MESSAGE_SUBSCRIPTION);
  }

  @Test
  void testGetEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(EventEntity.class);
  }

  @ParameterizedTest
  @EnumSource(
      value = ProcessMessageSubscriptionIntent.class,
      names = {"CREATED", "MIGRATED"},
      mode = Mode.INCLUDE)
  void shouldHandleRecord(final Intent intent) {
    // given
    final Record<ProcessMessageSubscriptionRecordValue> record = generateRecord(intent);

    // when - then
    assertThat(underTest.handlesRecord(record)).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = ProcessMessageSubscriptionIntent.class,
      names = {"CREATED", "MIGRATED"},
      mode = Mode.EXCLUDE)
  void shouldNotHandleRecord(final Intent intent) {
    // given
    final Record<ProcessMessageSubscriptionRecordValue> record = generateRecord(intent);

    // when - then
    assertThat(underTest.handlesRecord(record)).isFalse();
  }

  @Test
  void testGenerateIds() {
    // given
    final int processInstanceKey = 123;
    final int elementInstanceKey = 456;
    final var recordValue =
        ImmutableProcessMessageSubscriptionRecordValue.builder()
            .withProcessInstanceKey(processInstanceKey)
            .withElementInstanceKey(elementInstanceKey)
            .build();
    final Record<ProcessMessageSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.PROCESS_MESSAGE_SUBSCRIPTION, r -> r.withValue(recordValue));

    // when
    final var ids = underTest.generateIds(record);

    // then
    assertThat(ids)
        .containsExactly(String.format(ID_PATTERN, processInstanceKey, elementInstanceKey));
  }

  @Test
  void testCreateNewEntity() {
    // given
    final String id = "id";

    // when
    final var entity = underTest.createNewEntity(id);

    // then
    assertThat(entity.getId()).isEqualTo(id);
  }

  @Test
  public void testUpdateEntity() {
    // given
    final long recordKey = 789;
    final int partitionId = 10;
    final int position = 9999;
    final int processInstanceKey = 123;
    final int elementInstanceKey = 456;
    final String elementId = "elementId";
    final String bpmnProcessId = "bpmnProcessId";
    final String tenantId = "tenantId";
    final String messageName = "messageName";
    final String correlationKey = "correlationKey";
    final Intent intent = ProcessMessageSubscriptionIntent.CREATED;
    final var recordValue =
        ImmutableProcessMessageSubscriptionRecordValue.builder()
            .withProcessInstanceKey(processInstanceKey)
            .withElementInstanceKey(elementInstanceKey)
            .withElementId(elementId)
            .withBpmnProcessId(bpmnProcessId)
            .withTenantId(tenantId)
            .withMessageName(messageName)
            .withCorrelationKey(correlationKey)
            .build();
    final Record<ProcessMessageSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
            r ->
                r.withIntent(intent)
                    .withKey(recordKey)
                    .withPartitionId(partitionId)
                    .withPosition(position)
                    .withValue(recordValue));

    // when
    final EventEntity entity = new EventEntity();
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getId())
        .isEqualTo(String.format(ID_PATTERN, processInstanceKey, elementInstanceKey));
    assertThat(entity.getKey()).isEqualTo(recordKey);
    assertThat(entity.getPartitionId()).isEqualTo(partitionId);
    assertThat(entity.getEventSourceType()).isEqualTo(EventSourceType.PROCESS_MESSAGE_SUBSCRIPTION);
    assertThat(entity.getEventType()).isEqualTo(EventType.fromZeebeIntent(intent.name()));
    assertThat(entity.getProcessInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(entity.getFlowNodeInstanceKey()).isEqualTo(elementInstanceKey);
    assertThat(entity.getFlowNodeId()).isEqualTo(elementId);
    assertThat(entity.getBpmnProcessId()).isEqualTo(bpmnProcessId);
    assertThat(entity.getTenantId()).isEqualTo(tenantId);
    assertThat(entity.getPositionProcessMessageSubscription()).isEqualTo(position);
    assertThat(entity.getMetadata().getMessageName()).isEqualTo(messageName);
    assertThat(entity.getMetadata().getCorrelationKey()).isEqualTo(correlationKey);
  }

  @Test
  void shouldAddEntityOnFlush() {
    // given
    final String expectedIndexName = EventTemplate.INDEX_NAME;
    final String id = "555";
    final long position = 456L;
    final long key = 333L;

    final EventMetadataEntity metadata = new EventMetadataEntity();
    metadata.setMessageName("messageName");
    metadata.setCorrelationKey("correlationKey");
    final EventEntity inputEntity =
        new EventEntity()
            .setId(id)
            .setKey(key)
            .setPositionProcessMessageSubscription(position)
            .setMetadata(metadata);

    final Map<String, Object> expectedUpdateFields = new LinkedHashMap<>();
    expectedUpdateFields.put("key", key);
    expectedUpdateFields.put("positionProcessMessageSubscription", position);
    expectedUpdateFields.put("dateTime", null);
    expectedUpdateFields.put("flowNodeId", null);
    expectedUpdateFields.put("eventSourceType", null);
    expectedUpdateFields.put("eventType", null);
    expectedUpdateFields.put("bpmnProcessId", null);
    expectedUpdateFields.put("processDefinitionKey", null);

    final Map<String, Object> metadataMap = new LinkedHashMap<>();
    metadataMap.put(MESSAGE_NAME, metadata.getMessageName());
    metadataMap.put(CORRELATION_KEY, metadata.getCorrelationKey());
    expectedUpdateFields.put("metadata", metadataMap);

    final BatchRequest mockRequest = Mockito.mock(BatchRequest.class);

    // when
    underTest.flush(inputEntity, mockRequest);

    // then
    verify(mockRequest, times(1)).upsert(expectedIndexName, id, inputEntity, expectedUpdateFields);
  }

  private Record<ProcessMessageSubscriptionRecordValue> generateRecord(final Intent intent) {
    return factory.generateRecord(
        ValueType.PROCESS_MESSAGE_SUBSCRIPTION, r -> r.withIntent(intent));
  }
}
