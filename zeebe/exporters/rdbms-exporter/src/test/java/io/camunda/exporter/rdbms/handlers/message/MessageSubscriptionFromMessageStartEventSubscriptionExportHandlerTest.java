/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.write.domain.MessageSubscriptionDbModel;
import io.camunda.db.rdbms.write.service.MessageSubscriptionWriter;
import io.camunda.exporter.rdbms.handlers.MessageSubscriptionFromMessageStartEventSubscriptionExportHandler;
import io.camunda.exporter.rdbms.utils.DateUtil;
import io.camunda.search.entities.MessageSubscriptionEntity.MessageSubscriptionState;
import io.camunda.search.entities.MessageSubscriptionEntity.MessageSubscriptionType;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCache;
import io.camunda.zeebe.exporter.common.cache.process.CachedProcessEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableMessageStartEventSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.MessageStartEventSubscriptionRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

final class MessageSubscriptionFromMessageStartEventSubscriptionExportHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final MessageSubscriptionWriter writer = mock(MessageSubscriptionWriter.class);

  @SuppressWarnings("unchecked")
  private final ExporterEntityCache<Long, CachedProcessEntity> processCache =
      mock(ExporterEntityCache.class);

  private final MessageSubscriptionFromMessageStartEventSubscriptionExportHandler underTest =
      new MessageSubscriptionFromMessageStartEventSubscriptionExportHandler(writer, processCache);

  @ParameterizedTest
  @EnumSource(MessageStartEventSubscriptionIntent.class)
  void shouldHandleIntents(final Intent intent) {
    // given
    final Record<MessageStartEventSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.MESSAGE_START_EVENT_SUBSCRIPTION, b -> b.withIntent(intent));

    // when / then
    assertThat(underTest.canExport(record)).isTrue();
  }

  @Test
  void shouldCallCreateForCreatedIntent() {
    // given
    final Record<MessageStartEventSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.MESSAGE_START_EVENT_SUBSCRIPTION,
            b -> b.withIntent(MessageStartEventSubscriptionIntent.CREATED));
    when(processCache.get(record.getValue().getProcessDefinitionKey()))
        .thenReturn(Optional.empty());

    // when
    underTest.export(record);

    // then
    verify(writer).create(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void shouldCallUpdateForCorrelatedIntent() {
    // given
    final Record<MessageStartEventSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.MESSAGE_START_EVENT_SUBSCRIPTION,
            b -> b.withIntent(MessageStartEventSubscriptionIntent.CORRELATED));
    when(processCache.get(record.getValue().getProcessDefinitionKey()))
        .thenReturn(Optional.empty());

    // when
    underTest.export(record);

    // then
    verify(writer).update(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void shouldCallUpdateForDeletedIntent() {
    // given
    final Record<MessageStartEventSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.MESSAGE_START_EVENT_SUBSCRIPTION,
            b -> b.withIntent(MessageStartEventSubscriptionIntent.DELETED));
    when(processCache.get(record.getValue().getProcessDefinitionKey()))
        .thenReturn(Optional.empty());

    // when
    underTest.export(record);

    // then
    verify(writer).update(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void shouldMapAllFieldsCorrectlyWithCacheHit() {
    // given
    final long recordKey = 100L;
    final long pdKey = 200L;
    final long processInstanceKey = 300L;
    final long rootProcessInstanceKey = 400L;
    final long flowNodeInstanceKey = 500L;
    final int partitionId = 3;
    final long timestamp = Instant.now().toEpochMilli();
    final String elementId = "startEvent1";
    final String bpmnProcessId = "process1";
    final String messageName = "MyMessage";
    final String correlationKey = "order-123";
    final String tenantId = "tenant-1";
    final String processName = "Process One";
    final int processVersion = 2;
    final Map<String, String> extProps = Map.of("io.camunda.tool:name", "myTool");

    final var recordValue =
        ImmutableMessageStartEventSubscriptionRecordValue.builder()
            .withBpmnProcessId(bpmnProcessId)
            .withStartEventId(elementId)
            .withMessageName(messageName)
            .withCorrelationKey(correlationKey)
            .withProcessDefinitionKey(pdKey)
            .withTenantId(tenantId)
            .build();

    final Record<MessageStartEventSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.MESSAGE_START_EVENT_SUBSCRIPTION,
            r ->
                r.withIntent(MessageStartEventSubscriptionIntent.CREATED)
                    .withKey(recordKey)
                    .withPartitionId(partitionId)
                    .withTimestamp(timestamp)
                    .withValue(recordValue));

    final CachedProcessEntity cached =
        new CachedProcessEntity(
            processName, processVersion, null, null, null, false, Map.of(elementId, extProps));
    when(processCache.get(pdKey)).thenReturn(Optional.of(cached));

    // when
    underTest.export(record);

    // then
    final var captor = ArgumentCaptor.forClass(MessageSubscriptionDbModel.class);
    verify(writer).create(captor.capture());
    final MessageSubscriptionDbModel model = captor.getValue();

    assertThat(model.messageSubscriptionKey()).isEqualTo(recordKey);
    assertThat(model.processDefinitionId()).isEqualTo(bpmnProcessId);
    assertThat(model.processDefinitionKey()).isEqualTo(pdKey);
    assertThat(model.processInstanceKey()).isNull();
    assertThat(model.rootProcessInstanceKey()).isNull();
    assertThat(model.flowNodeId()).isEqualTo(elementId);
    assertThat(model.flowNodeInstanceKey()).isNull();
    assertThat(model.messageSubscriptionState()).isEqualTo(MessageSubscriptionState.CREATED);
    assertThat(model.messageSubscriptionType()).isEqualTo(MessageSubscriptionType.START_EVENT);
    assertThat(model.messageName()).isEqualTo(messageName);
    assertThat(model.correlationKey()).isEqualTo(correlationKey);
    assertThat(model.tenantId()).isEqualTo(tenantId);
    assertThat(model.partitionId()).isEqualTo(partitionId);
    assertThat(model.dateTime())
        .isEqualTo(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(timestamp)));
    assertThat(model.processDefinitionName()).isEqualTo(processName);
    assertThat(model.processDefinitionVersion()).isEqualTo(processVersion);
    assertThat(model.extensionProperties()).isEqualTo(extProps);
  }

  @Test
  void shouldHandleMissingProcessCacheGracefully() {
    // given
    final long pdKey = 300L;
    final var recordValue =
        ImmutableMessageStartEventSubscriptionRecordValue.builder()
            .withProcessDefinitionKey(pdKey)
            .withBpmnProcessId("proc")
            .withStartEventId("start")
            .withMessageName("msg")
            .build();
    final Record<MessageStartEventSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.MESSAGE_START_EVENT_SUBSCRIPTION,
            r -> r.withIntent(MessageStartEventSubscriptionIntent.CREATED).withValue(recordValue));
    when(processCache.get(pdKey)).thenReturn(Optional.empty());

    // when
    underTest.export(record);

    // then — no exception, name/version/extensionProperties are absent
    final var captor = ArgumentCaptor.forClass(MessageSubscriptionDbModel.class);
    verify(writer).create(captor.capture());
    final MessageSubscriptionDbModel model = captor.getValue();
    assertThat(model.processDefinitionName()).isNull();
    assertThat(model.processDefinitionVersion()).isNull();
    assertThat(model.extensionProperties()).isEqualTo(Map.of());
  }
}
