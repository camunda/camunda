/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate.CORRELATION_KEY;
import static io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate.MESSAGE_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate;
import io.camunda.webapps.schema.entities.messagesubscription.EventSourceType;
import io.camunda.webapps.schema.entities.messagesubscription.MessageSubscriptionEntity;
import io.camunda.webapps.schema.entities.messagesubscription.MessageSubscriptionMetadataEntity;
import io.camunda.webapps.schema.entities.messagesubscription.MessageSubscriptionState;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCache;
import io.camunda.zeebe.exporter.common.cache.process.CachedProcessEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableMessageStartEventSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.MessageStartEventSubscriptionRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mockito;

final class MessageSubscriptionFromMessageStartEventSubscriptionHandlerTest {
  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = MessageSubscriptionTemplate.INDEX_NAME;

  @SuppressWarnings("unchecked")
  private final ExporterEntityCache<Long, CachedProcessEntity> processCache =
      Mockito.mock(ExporterEntityCache.class);

  private final MessageSubscriptionFromMessageStartEventSubscriptionHandler underTest =
      new MessageSubscriptionFromMessageStartEventSubscriptionHandler(indexName, processCache);

  @BeforeEach
  void setUp() {
    Mockito.when(processCache.get(Mockito.anyLong())).thenReturn(Optional.empty());
  }

  @Test
  void shouldHandleMessageStartEventSubscriptionValueType() {
    assertThat(underTest.getHandledValueType())
        .isEqualTo(ValueType.MESSAGE_START_EVENT_SUBSCRIPTION);
  }

  @Test
  void shouldHandleMessageSubscriptionEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(MessageSubscriptionEntity.class);
  }

  @ParameterizedTest
  @EnumSource(MessageStartEventSubscriptionIntent.class)
  void shouldHandleCreatedAndDeletedIntents(final Intent intent) {
    // given
    final Record<MessageStartEventSubscriptionRecordValue> record = generateRecord(intent);

    // when - then
    assertThat(underTest.handlesRecord(record)).isTrue();
  }

  @Test
  void shouldGenerateIdFromRecordKey() {
    // given
    final long key = 12345L;
    final Record<MessageStartEventSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.MESSAGE_START_EVENT_SUBSCRIPTION,
            r -> r.withIntent(MessageStartEventSubscriptionIntent.CREATED).withKey(key));

    // when
    final List<String> ids = underTest.generateIds(record);

    // then
    assertThat(ids).containsExactly(String.valueOf(key));
  }

  @Test
  void shouldUpdateEntityFieldsFromRecord() {
    // given
    final long recordKey = 789;
    final int partitionId = 10;
    final int position = 9999;
    final long processDefinitionKey = 999L;
    final String bpmnProcessId = "my-process";
    final String startEventId = "StartEvent_1";
    final String messageName = "myMessage";
    final String tenantId = "<default>";
    final String correlationKey = "correlationKey";
    final MessageStartEventSubscriptionIntent intent = MessageStartEventSubscriptionIntent.CREATED;

    final ImmutableMessageStartEventSubscriptionRecordValue value =
        ImmutableMessageStartEventSubscriptionRecordValue.builder()
            .withProcessDefinitionKey(processDefinitionKey)
            .withBpmnProcessId(bpmnProcessId)
            .withStartEventId(startEventId)
            .withMessageName(messageName)
            .withTenantId(tenantId)
            .withProcessInstanceKey(-1L)
            .withCorrelationKey(correlationKey)
            .build();

    final Record<MessageStartEventSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.MESSAGE_START_EVENT_SUBSCRIPTION,
            r ->
                r.withIntent(intent)
                    .withKey(recordKey)
                    .withPartitionId(partitionId)
                    .withPosition(position)
                    .withValue(value));

    // when
    final var ids = underTest.generateIds(record);
    final MessageSubscriptionEntity entity = underTest.createNewEntity(ids.getFirst());
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getId()).isEqualTo(String.valueOf(recordKey));
    assertThat(entity.getKey()).isEqualTo(recordKey);
    assertThat(entity.getPartitionId()).isEqualTo(partitionId);
    assertThat(entity.getEventSourceType())
        .isEqualTo(EventSourceType.MESSAGE_START_EVENT_SUBSCRIPTION);
    assertThat(entity.getEventType())
        .isEqualTo(MessageSubscriptionState.fromZeebeIntent(intent.name()));
    assertThat(entity.getMessageSubscriptionType()).isEqualTo("START_EVENT");
    assertThat(entity.getProcessInstanceKey()).isNull();
    assertThat(entity.getFlowNodeInstanceKey()).isNull();
    assertThat(entity.getFlowNodeId()).isEqualTo(startEventId);
    assertThat(entity.getBpmnProcessId()).isEqualTo(bpmnProcessId);
    assertThat(entity.getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
    assertThat(entity.getTenantId()).isEqualTo(tenantId);
    assertThat(entity.getPositionProcessMessageSubscription()).isEqualTo(position);
    assertThat(entity.getMetadata().getMessageName()).isEqualTo(messageName);
    assertThat(entity.getMetadata().getCorrelationKey()).isEqualTo(correlationKey);
    assertThat(entity.getRootProcessInstanceKey()).isNull();
  }

  @Test
  void shouldSetProcessDefinitionNameFromCache() {
    // given
    final long processDefinitionKey = 100L;
    final String processName = "My Process";
    Mockito.when(processCache.get(processDefinitionKey))
        .thenReturn(
            Optional.of(
                new CachedProcessEntity(
                    processName, 3, "v3", List.of(), Map.of(), false, Map.of())));

    final ImmutableMessageStartEventSubscriptionRecordValue value =
        ImmutableMessageStartEventSubscriptionRecordValue.builder()
            .withProcessDefinitionKey(processDefinitionKey)
            .withBpmnProcessId("proc")
            .withStartEventId("StartEvent_1")
            .withMessageName("msg")
            .withTenantId("<default>")
            .withProcessInstanceKey(-1L)
            .withCorrelationKey("")
            .withMessageKey(-1L)
            .build();

    final Record<MessageStartEventSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.MESSAGE_START_EVENT_SUBSCRIPTION,
            r -> r.withIntent(MessageStartEventSubscriptionIntent.CREATED).withValue(value));

    final MessageSubscriptionEntity entity = new MessageSubscriptionEntity();

    // when
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getProcessDefinitionName()).isEqualTo(processName);
    assertThat(entity.getProcessDefinitionVersion()).isEqualTo(3);
  }

  @Test
  void shouldHandleMissingProcessCacheGracefully() {
    // given
    Mockito.when(processCache.get(Mockito.anyLong())).thenReturn(Optional.empty());

    final ImmutableMessageStartEventSubscriptionRecordValue value =
        ImmutableMessageStartEventSubscriptionRecordValue.builder()
            .withProcessDefinitionKey(999L)
            .withBpmnProcessId("proc")
            .withStartEventId("StartEvent_1")
            .withMessageName("msg")
            .withTenantId("<default>")
            .withProcessInstanceKey(-1L)
            .withCorrelationKey("")
            .withMessageKey(-1L)
            .build();

    final Record<MessageStartEventSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.MESSAGE_START_EVENT_SUBSCRIPTION,
            r -> r.withIntent(MessageStartEventSubscriptionIntent.CREATED).withValue(value));

    final MessageSubscriptionEntity entity = new MessageSubscriptionEntity();

    // when - then (no exception)
    underTest.updateEntity(record, entity);
    assertThat(entity.getProcessDefinitionName()).isNull();
    assertThat(entity.getProcessDefinitionVersion()).isNull();
  }

  @Test
  void shouldFlushEntityToIndex() {
    // given
    final String expectedIndexName = MessageSubscriptionTemplate.INDEX_NAME;
    final String id = "555";
    final long position = 456L;
    final long key = 333L;

    final MessageSubscriptionMetadataEntity metadata = new MessageSubscriptionMetadataEntity();
    metadata.setMessageName("messageName");
    metadata.setCorrelationKey("correlationKey");
    final MessageSubscriptionEntity inputEntity =
        new MessageSubscriptionEntity()
            .setId(id)
            .setKey(key)
            .setPositionProcessMessageSubscription(position)
            .setMetadata(metadata);

    final Map<String, Object> metadataMap = new LinkedHashMap<>();
    metadataMap.put(MESSAGE_NAME, metadata.getMessageName());
    metadataMap.put(CORRELATION_KEY, metadata.getCorrelationKey());

    final Map<String, Object> expectedUpdateFields = new LinkedHashMap<>();
    expectedUpdateFields.put("key", key);
    expectedUpdateFields.put("positionProcessMessageSubscription", position);
    expectedUpdateFields.put("dateTime", null);
    expectedUpdateFields.put("flowNodeId", null);
    expectedUpdateFields.put("eventSourceType", null);
    expectedUpdateFields.put("eventType", null);
    expectedUpdateFields.put("messageSubscriptionType", null);
    expectedUpdateFields.put("bpmnProcessId", null);
    expectedUpdateFields.put("processDefinitionKey", null);
    expectedUpdateFields.put("processDefinitionName", null);
    expectedUpdateFields.put("processDefinitionVersion", null);
    expectedUpdateFields.put("extensionProperties", null);
    expectedUpdateFields.put("metadata", metadataMap);
    final BatchRequest mockRequest = Mockito.mock(BatchRequest.class);

    // when
    underTest.flush(inputEntity, mockRequest);

    // then
    verify(mockRequest, times(1)).upsert(expectedIndexName, id, inputEntity, expectedUpdateFields);
  }

  private Record<MessageStartEventSubscriptionRecordValue> generateRecord(final Intent intent) {
    return factory.generateRecord(
        ValueType.MESSAGE_START_EVENT_SUBSCRIPTION, r -> r.withIntent(intent));
  }
}
