/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.write.domain.MessageSubscriptionDbModel;
import io.camunda.db.rdbms.write.service.MessageSubscriptionWriter;
import io.camunda.exporter.rdbms.handlers.MessageSubscriptionExportHandler;
import io.camunda.exporter.rdbms.utils.DateUtil;
import io.camunda.search.entities.MessageSubscriptionEntity.MessageSubscriptionState;
import io.camunda.search.entities.MessageSubscriptionEntity.MessageSubscriptionType;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCache;
import io.camunda.zeebe.exporter.common.cache.process.CachedProcessEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableProcessMessageSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessMessageSubscriptionRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

final class MessageSubscriptionExportHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final MessageSubscriptionWriter writer = mock(MessageSubscriptionWriter.class);

  @SuppressWarnings("unchecked")
  private final ExporterEntityCache<Long, CachedProcessEntity> processCache =
      mock(ExporterEntityCache.class);

  private final MessageSubscriptionExportHandler underTest =
      new MessageSubscriptionExportHandler(writer, processCache);

  @ParameterizedTest
  @EnumSource(
      value = ProcessMessageSubscriptionIntent.class,
      names = {"CREATED", "CORRELATED", "DELETED", "MIGRATED"},
      mode = Mode.INCLUDE)
  void shouldHandleExpectedIntents(final Intent intent) {
    // given
    final Record<ProcessMessageSubscriptionRecordValue> record =
        factory.generateRecord(ValueType.PROCESS_MESSAGE_SUBSCRIPTION, b -> b.withIntent(intent));

    // when / then
    assertThat(underTest.canExport(record)).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = ProcessMessageSubscriptionIntent.class,
      names = {"CREATED", "CORRELATED", "DELETED", "MIGRATED"},
      mode = Mode.EXCLUDE)
  void shouldNotHandleOtherIntents(final Intent intent) {
    // given
    final Record<ProcessMessageSubscriptionRecordValue> record =
        factory.generateRecord(ValueType.PROCESS_MESSAGE_SUBSCRIPTION, b -> b.withIntent(intent));

    // when / then
    assertThat(underTest.canExport(record)).isFalse();
  }

  @Test
  void shouldCallCreateForCreatedIntent() {
    // given
    final Record<ProcessMessageSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
            b -> b.withIntent(ProcessMessageSubscriptionIntent.CREATED));
    when(processCache.get(record.getValue().getProcessDefinitionKey()))
        .thenReturn(Optional.empty());

    // when
    underTest.export(record);

    // then
    verify(writer).create(any());
  }

  @ParameterizedTest
  @EnumSource(
      value = ProcessMessageSubscriptionIntent.class,
      names = {"CORRELATED", "DELETED", "MIGRATED"},
      mode = Mode.INCLUDE)
  void shouldCallUpdateForNonCreatedIntents(final Intent intent) {
    // given
    final Record<ProcessMessageSubscriptionRecordValue> record =
        factory.generateRecord(ValueType.PROCESS_MESSAGE_SUBSCRIPTION, b -> b.withIntent(intent));
    when(processCache.get(record.getValue().getProcessDefinitionKey()))
        .thenReturn(Optional.empty());

    // when
    underTest.export(record);

    // then
    verify(writer).update(any());
  }

  @ParameterizedTest
  @MethodSource("provideTestRoutines")
  void shouldMapAllFieldsCorrectlyWithCacheHit(
      final Intent intent,
      final MessageSubscriptionState state,
      final BiConsumer<MessageSubscriptionWriter, ArgumentCaptor<MessageSubscriptionDbModel>>
          testRoutine) {
    // given
    final long recordKey = 100L;
    final long pdKey = 200L;
    final long processInstanceKey = 300L;
    final long rootProcessInstanceKey = 400L;
    final long flowNodeInstanceKey = 500L;
    final int partitionId = 3;
    final long timestamp = Instant.now().toEpochMilli();
    final String elementId = "catchEvent1";
    final String bpmnProcessId = "process1";
    final String messageName = "MyMessage";
    final String correlationKey = "order-123";
    final String tenantId = "tenant-1";
    final String processName = "Process One";
    final int processVersion = 2;
    final Map<String, String> extProps =
        Map.of("io.camunda.tool:name", "myTool", "inbound.type", "io.camunda:http-webhook:1");

    final var recordValue =
        ImmutableProcessMessageSubscriptionRecordValue.builder()
            .withBpmnProcessId(bpmnProcessId)
            .withElementId(elementId)
            .withMessageName(messageName)
            .withCorrelationKey(correlationKey)
            .withProcessDefinitionKey(pdKey)
            .withProcessInstanceKey(processInstanceKey)
            .withRootProcessInstanceKey(rootProcessInstanceKey)
            .withElementInstanceKey(flowNodeInstanceKey)
            .withTenantId(tenantId)
            .build();

    final Record<ProcessMessageSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
            r ->
                r.withIntent(intent)
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
    testRoutine.accept(writer, captor);
    final MessageSubscriptionDbModel model = captor.getValue();

    assertThat(model.messageSubscriptionKey()).isEqualTo(recordKey);
    assertThat(model.processDefinitionId()).isEqualTo(bpmnProcessId);
    assertThat(model.processDefinitionKey()).isEqualTo(pdKey);
    assertThat(model.processInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(model.rootProcessInstanceKey()).isEqualTo(rootProcessInstanceKey);
    assertThat(model.flowNodeId()).isEqualTo(elementId);
    assertThat(model.flowNodeInstanceKey()).isEqualTo(flowNodeInstanceKey);
    assertThat(model.messageSubscriptionState()).isEqualTo(state);
    assertThat(model.messageSubscriptionType()).isEqualTo(MessageSubscriptionType.PROCESS_EVENT);
    assertThat(model.messageName()).isEqualTo(messageName);
    assertThat(model.correlationKey()).isEqualTo(correlationKey);
    assertThat(model.tenantId()).isEqualTo(tenantId);
    assertThat(model.partitionId()).isEqualTo(partitionId);
    assertThat(model.dateTime())
        .isEqualTo(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(timestamp)));
    assertThat(model.processDefinitionName()).isEqualTo(processName);
    assertThat(model.processDefinitionVersion()).isEqualTo(processVersion);
    assertThat(model.extensionProperties()).isEqualTo(extProps);
    assertThat(model.toolName()).isEqualTo("myTool");
    assertThat(model.inboundConnectorType()).isEqualTo("io.camunda:http-webhook:1");
  }

  private static Stream<Arguments> provideTestRoutines() {
    return Stream.of(
        Arguments.of(
            ProcessMessageSubscriptionIntent.CREATED,
            MessageSubscriptionState.CREATED,
            (BiConsumer<MessageSubscriptionWriter, ArgumentCaptor<MessageSubscriptionDbModel>>)
                (writer, captor) -> verify(writer).create(captor.capture())),
        Arguments.of(
            ProcessMessageSubscriptionIntent.CORRELATED,
            MessageSubscriptionState.CORRELATED,
            (BiConsumer<MessageSubscriptionWriter, ArgumentCaptor<MessageSubscriptionDbModel>>)
                (writer, captor) -> verify(writer).update(captor.capture())));
  }

  @Test
  void shouldHandleMissingProcessCacheGracefully() {
    // given
    final long pdKey = 300L;
    final var recordValue =
        ImmutableProcessMessageSubscriptionRecordValue.builder()
            .withProcessDefinitionKey(pdKey)
            .withBpmnProcessId("proc")
            .withElementId("catch")
            .withMessageName("msg")
            .withCorrelationKey("key")
            .withProcessInstanceKey(1L)
            .withRootProcessInstanceKey(1L)
            .withElementInstanceKey(2L)
            .withVariables(Map.of())
            .build();
    final Record<ProcessMessageSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.PROCESS_MESSAGE_SUBSCRIPTION,
            r -> r.withIntent(ProcessMessageSubscriptionIntent.CREATED).withValue(recordValue));
    when(processCache.get(pdKey)).thenReturn(Optional.empty());

    // when
    underTest.export(record);

    // then — no exception, enrichment fields fall back to null / empty map
    final var captor = ArgumentCaptor.forClass(MessageSubscriptionDbModel.class);
    verify(writer).create(captor.capture());
    final MessageSubscriptionDbModel model = captor.getValue();
    assertThat(model.processDefinitionName()).isNull();
    assertThat(model.processDefinitionVersion()).isNull();
    assertThat(model.extensionProperties()).isEqualTo(Map.of());
    assertThat(model.toolName()).isNull();
    assertThat(model.inboundConnectorType()).isNull();
  }
}
