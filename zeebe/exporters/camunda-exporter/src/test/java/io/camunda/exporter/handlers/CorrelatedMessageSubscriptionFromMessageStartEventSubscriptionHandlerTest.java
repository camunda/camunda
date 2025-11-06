/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.exporter.utils.ExporterUtil;
import io.camunda.webapps.schema.descriptors.template.CorrelatedMessageSubscriptionTemplate;
import io.camunda.webapps.schema.entities.CorrelatedMessageSubscriptionEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.ImmutableMessageStartEventSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.MessageStartEventSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.mockito.Mockito;

final class CorrelatedMessageSubscriptionFromMessageStartEventSubscriptionHandlerTest {
  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = CorrelatedMessageSubscriptionTemplate.INDEX_NAME;

  private final CorrelatedMessageSubscriptionFromMessageStartEventSubscriptionHandler underTest =
      new CorrelatedMessageSubscriptionFromMessageStartEventSubscriptionHandler(indexName);

  @Test
  void shouldReturnExpectedHandledValueType() {
    assertThat(underTest.getHandledValueType())
        .isEqualTo(ValueType.MESSAGE_START_EVENT_SUBSCRIPTION);
  }

  @Test
  void shouldReturnExpectedEntityType() {
    assertThat(underTest.getEntityType()).isEqualTo(CorrelatedMessageSubscriptionEntity.class);
  }

  @Test
  void shouldHandleProcessMessageSubscriptionCorrelatedRecord() {
    // given
    final Record<MessageStartEventSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.MESSAGE_START_EVENT_SUBSCRIPTION,
            builder -> builder.withIntent(MessageStartEventSubscriptionIntent.CORRELATED));

    // when/then
    assertThat(underTest.handlesRecord(record)).isTrue();
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
    assertThat(underTest.handlesRecord(record)).isFalse();
  }

  @Test
  void shouldNotHandleWrongValueType() {
    // given
    final Record<MessageStartEventSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.MESSAGE,
            builder -> builder.withIntent(MessageStartEventSubscriptionIntent.CORRELATED));

    // when/then
    assertThat(underTest.handlesRecord(record)).isFalse();
  }

  @Test
  void shouldGenerateCompositeId() {
    // given
    final Record<MessageStartEventSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.MESSAGE_START_EVENT_SUBSCRIPTION,
            builder ->
                builder
                    .withIntent(MessageStartEventSubscriptionIntent.CORRELATED)
                    .withKey(12345L)
                    .withValue(
                        ImmutableMessageStartEventSubscriptionRecordValue.builder()
                            .withMessageKey(67890L)
                            .build()));

    // when
    final List<String> ids = underTest.generateIds(record);

    // then
    assertThat(ids).hasSize(1).containsExactly("67890_12345");
  }

  @Test
  void shouldCreateNewEntity() {
    // given
    final String id = "id";

    // when
    final var entity = underTest.createNewEntity(id);

    // then
    assertThat(entity).isNotNull().extracting("id").isEqualTo(id);
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

    final CorrelatedMessageSubscriptionEntity entity =
        underTest.createNewEntity(underTest.generateIds(record).getFirst());

    // when
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getId()).isEqualTo(messageKey + "_" + recordKey);
    assertThat(entity.getBpmnProcessId()).isEqualTo(bpmnProcessId);
    assertThat(entity.getCorrelationKey()).isEqualTo(correlationKey);
    assertThat(entity.getCorrelationTime())
        .isEqualTo(ExporterUtil.toOffsetDateTime(Instant.ofEpochMilli(timestamp)));
    assertThat(entity.getFlowNodeId()).isEqualTo(elementId);
    assertThat(entity.getFlowNodeInstanceKey()).isNull();
    assertThat(entity.getMessageKey()).isEqualTo(messageKey);
    assertThat(entity.getMessageName()).isEqualTo(messageName);
    assertThat(entity.getPartitionId()).isEqualTo(partitionId);
    assertThat(entity.getPosition()).isEqualTo(position);
    assertThat(entity.getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
    assertThat(entity.getProcessInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(entity.getSubscriptionKey()).isEqualTo(recordKey);
    assertThat(entity.getSubscriptionType()).isEqualTo("START_EVENT");
    assertThat(entity.getTenantId()).isEqualTo(tenantId);
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

    final CorrelatedMessageSubscriptionEntity entity =
        underTest.createNewEntity(underTest.generateIds(record).getFirst());

    // when
    underTest.updateEntity(record, entity);

    // then
    assertThat(entity.getTenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  @Test
  void shouldAddEntityOnFlush() {
    // given
    final long recordKey = 789;
    final long messageKey = 555;
    final Intent intent = MessageStartEventSubscriptionIntent.CORRELATED;
    final String expectedIndexName = CorrelatedMessageSubscriptionTemplate.INDEX_NAME;

    final var recordValue =
        ImmutableMessageStartEventSubscriptionRecordValue.builder()
            .withMessageKey(messageKey)
            .build();
    final Record<MessageStartEventSubscriptionRecordValue> record =
        factory.generateRecord(
            ValueType.MESSAGE_START_EVENT_SUBSCRIPTION,
            r -> r.withIntent(intent).withKey(recordKey).withValue(recordValue));

    final CorrelatedMessageSubscriptionEntity entity =
        underTest.createNewEntity(underTest.generateIds(record).getFirst());

    final BatchRequest mockRequest = Mockito.mock(BatchRequest.class);

    // when
    underTest.flush(entity, mockRequest);

    // then
    verify(mockRequest, times(1)).add(expectedIndexName, entity);
  }
}
