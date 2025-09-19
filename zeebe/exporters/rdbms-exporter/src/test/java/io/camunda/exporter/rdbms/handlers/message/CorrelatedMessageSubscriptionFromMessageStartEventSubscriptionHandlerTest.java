/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.mock;

import io.camunda.db.rdbms.write.domain.CorrelatedMessageSubscriptionDbModel;
import io.camunda.db.rdbms.write.service.CorrelatedMessageSubscriptionWriter;
import io.camunda.exporter.rdbms.handlers.CorrelatedMessageSubscriptionFromMessageStartEventSubscriptionExportHandler;
import io.camunda.exporter.rdbms.utils.DateUtil;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableMessageStartEventSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.MessageStartEventSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.mockito.ArgumentCaptor;

final class CorrelatedMessageSubscriptionFromMessageStartEventSubscriptionHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();

  private final CorrelatedMessageSubscriptionWriter correlatedMessageSubscriptionWriter =
      mock(CorrelatedMessageSubscriptionWriter.class);
  private final CorrelatedMessageSubscriptionFromMessageStartEventSubscriptionExportHandler underTest =
      new CorrelatedMessageSubscriptionFromMessageStartEventSubscriptionExportHandler(correlatedMessageSubscriptionWriter);

  @Test
  void shouldHandleProcessMessageSubscriptionCorrelatedRecord() {
    // given
    final Record<MessageStartEventSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.MESSAGE_START_EVENT_SUBSCRIPTION,
            builder -> builder.withIntent(MessageStartEventSubscriptionIntent.CORRELATED));

    // when/then
    assertThat(underTest.canExport(record)).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = MessageStartEventSubscriptionIntent.class,
      names = {"CORRELATED"},
      mode = Mode.EXCLUDE)
  void shouldNotHandleRecord(final Intent intent) {
    // given
    final Record<MessageStartEventSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.MESSAGE_START_EVENT_SUBSCRIPTION, builder -> builder.withIntent(intent));

    // when/then
    assertThat(underTest.canExport(record)).isFalse();
  }

  @Test
  void shouldNotHandleWrongValueType() {
    // given
    final Record<MessageStartEventSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.MESSAGE,
            builder -> builder.withIntent(MessageStartEventSubscriptionIntent.CORRELATED));

    // when/then
    assertThat(underTest.canExport(record)).isFalse();
  }

  @Test
  public void shouldUpdateEntityWithAllAttributes() {
    // given
    final long recordKey = 789;
    final long messageKey = 555;
    final int partitionId = 10;
    final int position = 9999;
    final int processInstanceKey = 123;
    final int processDefinitionKey = 456;
    final long timestamp = Instant.now().toEpochMilli();
    final String elementId = "elementId";
    final String bpmnProcessId = "bpmnProcessId";
    final String tenantId = "tenantId";
    final String messageName = "messageName";
    final String correlationKey = "correlationKey";
    final Intent intent = MessageStartEventSubscriptionIntent.CORRELATED;
    final var recordValue =
        ImmutableMessageStartEventSubscriptionRecordValue.builder()
            .withBpmnProcessId(bpmnProcessId)
            .withCorrelationKey(correlationKey)
            .withStartEventId(elementId)
            .withMessageKey(messageKey)
            .withMessageName(messageName)
            .withProcessInstanceKey(processInstanceKey)
            .withProcessDefinitionKey(processDefinitionKey)
            .withTenantId(tenantId)
            .build();
    final Record<MessageStartEventSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.MESSAGE_START_EVENT_SUBSCRIPTION,
            r ->
                r.withIntent(intent)
                    .withKey(recordKey)
                    .withPartitionId(partitionId)
                    .withPosition(position)
                    .withTimestamp(timestamp)
                    .withValue(recordValue));

    // when
    underTest.export(record);

    // then
    final var itemCaptor = ArgumentCaptor.forClass(CorrelatedMessageSubscriptionDbModel.class);
    verify(correlatedMessageSubscriptionWriter).create(itemCaptor.capture());
    final CorrelatedMessageSubscriptionDbModel entity = itemCaptor.getValue();

    assertThat(entity.correlationKey()).isEqualTo(correlationKey);
    assertThat(entity.correlationTime())
        .isEqualTo(DateUtil.toOffsetDateTime(Instant.ofEpochMilli(timestamp)));
    assertThat(entity.flowNodeId()).isEqualTo(elementId);
    assertThat(entity.flowNodeInstanceKey()).isNull();
    assertThat(entity.historyCleanupDate()).isNull();
    assertThat(entity.messageKey()).isEqualTo(messageKey);
    assertThat(entity.messageName()).isEqualTo(messageName);
    assertThat(entity.partitionId()).isEqualTo(partitionId);
    assertThat(entity.processDefinitionId()).isEqualTo(bpmnProcessId);
    assertThat(entity.processDefinitionKey()).isEqualTo(processDefinitionKey);
    assertThat(entity.processInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(entity.subscriptionKey()).isEqualTo(recordKey);
    assertThat(entity.tenantId()).isEqualTo(tenantId);
  }

  @Test
  void shouldUpdateEntityWithDefaultTenant() {
    // given
    final long recordKey = 789;
    final long messageKey = 555;
    final Intent intent = MessageStartEventSubscriptionIntent.CORRELATED;

    final var recordValue =
        ImmutableMessageStartEventSubscriptionRecordValue.builder()
            .withMessageKey(messageKey)
            .build();
    final Record<MessageStartEventSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.MESSAGE_START_EVENT_SUBSCRIPTION,
            r -> r.withIntent(intent).withKey(recordKey).withValue(recordValue));

    // when
    underTest.export(record);

    // then
    final var itemCaptor = ArgumentCaptor.forClass(CorrelatedMessageSubscriptionDbModel.class);
    verify(correlatedMessageSubscriptionWriter).create(itemCaptor.capture());
    final CorrelatedMessageSubscriptionDbModel entity = itemCaptor.getValue();
    assertThat(entity.tenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }
}
