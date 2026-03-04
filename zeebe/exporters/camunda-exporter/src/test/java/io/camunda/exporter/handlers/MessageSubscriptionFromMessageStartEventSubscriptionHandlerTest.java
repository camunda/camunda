/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.search.entities.MessageSubscriptionEntity.MessageSubscriptionType;
import io.camunda.webapps.schema.descriptors.template.MessageSubscriptionTemplate;
import io.camunda.webapps.schema.entities.messagesubscription.EventSourceType;
import io.camunda.webapps.schema.entities.messagesubscription.MessageSubscriptionEntity;
import io.camunda.webapps.schema.entities.messagesubscription.MessageSubscriptionState;
import io.camunda.webapps.schema.util.ExtensionPropertyKeyUtil;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCache;
import io.camunda.zeebe.exporter.common.cache.process.CachedProcessEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableMessageStartEventSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.MessageStartEventSubscriptionRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import io.camunda.zeebe.util.modelreader.FlowNodeMetadata;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.mockito.Mockito;

final class MessageSubscriptionFromMessageStartEventSubscriptionHandlerTest {
  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = MessageSubscriptionTemplate.INDEX_NAME;

  @SuppressWarnings("unchecked")
  private final ExporterEntityCache<Long, CachedProcessEntity> processCache =
      mock(ExporterEntityCache.class);

  private final MessageSubscriptionFromMessageStartEventSubscriptionHandler underTest =
      new MessageSubscriptionFromMessageStartEventSubscriptionHandler(indexName, processCache);

  @BeforeEach
  void setUp() {
    when(processCache.get(Mockito.anyLong())).thenReturn(Optional.empty());
  }

  @Test
  void shouldReturnExpectedHandledValueType() {
    assertThat(underTest.getHandledValueType())
        .isEqualTo(ValueType.MESSAGE_START_EVENT_SUBSCRIPTION);
  }

  @Test
  void shouldReturnExpectedEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(MessageSubscriptionEntity.class);
  }

  @ParameterizedTest
  @EnumSource(
      value = MessageStartEventSubscriptionIntent.class,
      names = {"CREATED", "DELETED"},
      mode = Mode.INCLUDE)
  void shouldHandleRecord(final Intent intent) {
    final Record<MessageStartEventSubscriptionRecordValue> record = generateRecord(intent);
    assertThat(underTest.handlesRecord(record)).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = MessageStartEventSubscriptionIntent.class,
      names = {"CREATED", "DELETED"},
      mode = Mode.EXCLUDE)
  void shouldNotHandleRecord(final Intent intent) {
    final Record<MessageStartEventSubscriptionRecordValue> record = generateRecord(intent);
    assertThat(underTest.handlesRecord(record)).isFalse();
  }

  @Test
  void testGenerateIds() {
    // given
    final long recordKey = 123L;
    final Record<MessageStartEventSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.MESSAGE_START_EVENT_SUBSCRIPTION,
            r -> r.withKey(recordKey).withIntent(MessageStartEventSubscriptionIntent.CREATED));

    // when
    final var ids = underTest.generateIds(record);

    // then
    assertThat(ids).containsExactly(String.valueOf(recordKey));
  }

  @Test
  void testCreateNewEntity() {
    final var entity = underTest.createNewEntity("id");
    assertThat(entity.getId()).isEqualTo("id");
  }

  @Test
  void testUpdateEntity() {
    // given
    final long recordKey = 789L;
    final int partitionId = 1;
    final int position = 9999;
    final long processDefinitionKey = 555L;
    final String startEventId = "startEvent_1";
    final String bpmnProcessId = "bpmnProcessId";
    final String tenantId = "tenantId";
    final String messageName = "messageName";
    final Intent intent = MessageStartEventSubscriptionIntent.CREATED;
    final var recordValue =
        ImmutableMessageStartEventSubscriptionRecordValue.builder()
            .withProcessDefinitionKey(processDefinitionKey)
            .withStartEventId(startEventId)
            .withBpmnProcessId(bpmnProcessId)
            .withTenantId(tenantId)
            .withMessageName(messageName)
            .build();
    final Record<MessageStartEventSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.MESSAGE_START_EVENT_SUBSCRIPTION,
            r ->
                r.withIntent(intent)
                    .withKey(recordKey)
                    .withPartitionId(partitionId)
                    .withPosition(position)
                    .withValue(recordValue));

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
    assertThat(entity.getFlowNodeId()).isEqualTo(startEventId);
    assertThat(entity.getBpmnProcessId()).isEqualTo(bpmnProcessId);
    assertThat(entity.getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
    assertThat(entity.getTenantId()).isEqualTo(tenantId);
    assertThat(entity.getMetadata().getMessageName()).isEqualTo(messageName);
    assertThat(entity.getMessageSubscriptionType())
        .isEqualTo(MessageSubscriptionType.START_EVENT_SUBSCRIPTION.name());
    // No process instance fields for start event subscriptions
    assertThat(entity.getProcessInstanceKey()).isNull();
    assertThat(entity.getFlowNodeInstanceKey()).isNull();
  }

  @Test
  void shouldEnrichWithProcessDefinitionInfoFromCache() {
    // given
    final long processDefinitionKey = 555L;
    final String processName = "My Process";
    final int processVersion = 2;
    final var cachedProcess =
        new CachedProcessEntity(processName, processVersion, null, null, null);
    when(processCache.get(processDefinitionKey)).thenReturn(Optional.of(cachedProcess));

    final var recordValue =
        ImmutableMessageStartEventSubscriptionRecordValue.builder()
            .withProcessDefinitionKey(processDefinitionKey)
            .build();
    final Record<MessageStartEventSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.MESSAGE_START_EVENT_SUBSCRIPTION,
            r -> r.withIntent(MessageStartEventSubscriptionIntent.CREATED).withValue(recordValue));
    final var entity = new MessageSubscriptionEntity();

    // when
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getProcessDefinitionName()).isEqualTo(processName);
    assertThat(entity.getProcessDefinitionVersion()).isEqualTo(processVersion);
  }

  @Test
  void shouldEncodeExtensionPropertyKeysWithDotsFromCache() {
    // given
    final long processDefinitionKey = 555L;
    final var cachedProcess =
        new CachedProcessEntity(
            "My Process",
            2,
            null,
            null,
            Map.of(
                "start_event_1",
                new FlowNodeMetadata("Start event", Map.of("io.camunda.test:foo", "bar"))));
    when(processCache.get(processDefinitionKey)).thenReturn(Optional.of(cachedProcess));

    final var recordValue =
        ImmutableMessageStartEventSubscriptionRecordValue.builder()
            .withProcessDefinitionKey(processDefinitionKey)
            .withStartEventId("start_event_1")
            .build();
    final Record<MessageStartEventSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.MESSAGE_START_EVENT_SUBSCRIPTION,
            r -> r.withIntent(MessageStartEventSubscriptionIntent.CREATED).withValue(recordValue));
    final var entity = new MessageSubscriptionEntity();

    // when
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getExtensionProperties())
        .containsEntry(ExtensionPropertyKeyUtil.encode("io.camunda.test:foo"), "bar");
  }

  @Test
  void shouldFlushEntityWithZeroPosition() {
    // given
    final String id = "555";
    final long key = 333L;
    final MessageSubscriptionEntity inputEntity =
        new MessageSubscriptionEntity().setId(id).setKey(key);

    final BatchRequest mockRequest = Mockito.mock(BatchRequest.class);

    // when
    underTest.flush(inputEntity, mockRequest);

    // then - verify upsert was called (position=0 for start event subscriptions)
    verify(mockRequest, times(1))
        .upsert(Mockito.eq(indexName), Mockito.eq(id), Mockito.eq(inputEntity), Mockito.anyMap());
  }

  private Record<MessageStartEventSubscriptionRecordValue> generateRecord(final Intent intent) {
    return factory.generateRecord(
        ValueType.MESSAGE_START_EVENT_SUBSCRIPTION, r -> r.withIntent(intent));
  }
}
