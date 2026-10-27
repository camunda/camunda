/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.waitstate;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.exporter.common.waitstate.WaitStateEntry.WaitStateType;
import io.camunda.zeebe.exporter.common.waitstate.transformers.MessageBasedWaitStateTransformer;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ImmutableMessageSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.MessageSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * End-to-end integration test for the message-subscription wait-state handler triple produced by
 * {@link WaitStateHandlerBuilder} with {@link MessageBasedWaitStateTransformer}.
 *
 * <p>Validates: {@code CREATED} writes the entry, {@code MIGRATED} upserts it, and {@code
 * CORRELATED}/{@code DELETED} remove it. Only {@link BpmnElementType#RECEIVE_TASK} and {@link
 * BpmnElementType#INTERMEDIATE_CATCH_EVENT} subscriptions are tracked.
 */
class MessageWaitStateHandlerTest {

  private static final String INDEX_NAME = "test-wait-state";
  private static final long RECORD_KEY = 999L;

  private final ProtocolFactory factory = new ProtocolFactory();
  private final ObjectMapper objectMapper = new ObjectMapper();

  private WaitStateAddHandler<MessageSubscriptionRecordValue> addHandler;
  private WaitStateUpdateHandler<MessageSubscriptionRecordValue> updateHandler;
  private WaitStateRemoveHandler<MessageSubscriptionRecordValue> removeHandler;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    final var handlers =
        WaitStateHandlerBuilder.of(INDEX_NAME, objectMapper)
            .addTransformer(new MessageBasedWaitStateTransformer())
            .build();

    addHandler =
        (WaitStateAddHandler<MessageSubscriptionRecordValue>)
            handlers.stream()
                .filter(h -> h instanceof WaitStateAddHandler)
                .findFirst()
                .orElseThrow();
    updateHandler =
        (WaitStateUpdateHandler<MessageSubscriptionRecordValue>)
            handlers.stream()
                .filter(h -> h instanceof WaitStateUpdateHandler)
                .findFirst()
                .orElseThrow();
    removeHandler =
        (WaitStateRemoveHandler<MessageSubscriptionRecordValue>)
            handlers.stream()
                .filter(h -> h instanceof WaitStateRemoveHandler)
                .findFirst()
                .orElseThrow();
  }

  @Test
  void shouldBuilderProduceExactlyThreeHandlers() {
    // when
    final var handlers =
        WaitStateHandlerBuilder.of(INDEX_NAME, objectMapper)
            .addTransformer(new MessageBasedWaitStateTransformer())
            .build();

    // then — one add + one update + one remove
    assertThat(handlers).hasSize(3);
    assertThat(handlers.stream().filter(h -> h instanceof WaitStateAddHandler).count())
        .isEqualTo(1);
    assertThat(handlers.stream().filter(h -> h instanceof WaitStateUpdateHandler).count())
        .isEqualTo(1);
    assertThat(handlers.stream().filter(h -> h instanceof WaitStateRemoveHandler).count())
        .isEqualTo(1);
  }

  @Test
  void shouldAddHandlerAcceptCreatedForReceiveTaskAndIntermediateCatchEvent() {
    // given
    final var receiveTaskCreated =
        record(MessageSubscriptionIntent.CREATED, BpmnElementType.RECEIVE_TASK);
    final var catchEventCreated =
        record(MessageSubscriptionIntent.CREATED, BpmnElementType.INTERMEDIATE_CATCH_EVENT);

    // when / then
    assertThat(addHandler.handlesRecord(receiveTaskCreated)).isTrue();
    assertThat(addHandler.handlesRecord(catchEventCreated)).isTrue();
  }

  @Test
  void shouldAllHandlersRejectBoundaryEventSubscriptions() {
    // given — boundary events represent interrupts, not explicit waits; they must never be tracked
    final var created = record(MessageSubscriptionIntent.CREATED, BpmnElementType.BOUNDARY_EVENT);
    final var migrated = record(MessageSubscriptionIntent.MIGRATED, BpmnElementType.BOUNDARY_EVENT);
    final var correlated =
        record(MessageSubscriptionIntent.CORRELATED, BpmnElementType.BOUNDARY_EVENT);

    // when / then
    assertThat(addHandler.handlesRecord(created)).isFalse();
    assertThat(updateHandler.handlesRecord(migrated)).isFalse();
    assertThat(removeHandler.handlesRecord(correlated)).isFalse();
  }

  @Test
  void shouldUpdateHandlerAcceptMigratedAndRejectCreated() {
    // given
    final var migrated = record(MessageSubscriptionIntent.MIGRATED, BpmnElementType.RECEIVE_TASK);
    final var created = record(MessageSubscriptionIntent.CREATED, BpmnElementType.RECEIVE_TASK);

    // when / then
    assertThat(updateHandler.handlesRecord(migrated)).isTrue();
    assertThat(addHandler.handlesRecord(migrated)).isFalse();
    assertThat(removeHandler.handlesRecord(migrated)).isFalse();
    assertThat(updateHandler.handlesRecord(created)).isFalse();
  }

  @Test
  void shouldRemoveHandlerAcceptCorrelatedAndDeleted() {
    // given
    final var correlated =
        record(MessageSubscriptionIntent.CORRELATED, BpmnElementType.RECEIVE_TASK);
    final var deleted = record(MessageSubscriptionIntent.DELETED, BpmnElementType.RECEIVE_TASK);

    // when / then
    assertThat(removeHandler.handlesRecord(correlated)).isTrue();
    assertThat(removeHandler.handlesRecord(deleted)).isTrue();
    assertThat(addHandler.handlesRecord(correlated)).isFalse();
    assertThat(updateHandler.handlesRecord(correlated)).isFalse();
  }

  @Test
  void shouldWriteEntityWithMessageDetails() throws Exception {
    // given
    final var value =
        ImmutableMessageSubscriptionRecordValue.builder()
            .from(factory.generateObject(MessageSubscriptionRecordValue.class))
            .withMessageName("order-received")
            .withCorrelationKey("order-123")
            .withElementType(BpmnElementType.RECEIVE_TASK)
            .withElementId("receive-order")
            .withElementInstanceKey(300L)
            .withProcessInstanceKey(200L)
            .withRootProcessInstanceKey(100L)
            .withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .build();
    final var messageRecord =
        cast(
            factory.generateRecord(
                ValueType.MESSAGE_SUBSCRIPTION,
                r ->
                    r.withKey(RECORD_KEY)
                        .withRecordType(RecordType.EVENT)
                        .withIntent(MessageSubscriptionIntent.CREATED)
                        .withValue(value)));
    final var entity = addHandler.createNewEntity(String.valueOf(RECORD_KEY));

    // when
    addHandler.updateEntity(messageRecord, entity);

    // then
    assertThat(entity.getElementId()).isEqualTo("receive-order");
    assertThat(entity.getElementInstanceKey()).isEqualTo(300L);
    assertThat(entity.getProcessInstanceKey()).isEqualTo(200L);
    assertThat(entity.getRootProcessInstanceKey()).isEqualTo(100L);
    assertThat(entity.getWaitStateType()).isEqualTo(WaitStateType.MESSAGE.name());

    // then — message-specific details serialised as JSON
    final var details = objectMapper.readTree(entity.getDetails());
    assertThat(details.get("messageName").textValue()).isEqualTo("order-received");
    assertThat(details.get("correlationKey").textValue()).isEqualTo("order-123");
  }

  @Test
  void shouldBothHandlersUseTheSameDocumentId() {
    // given
    final var created = record(MessageSubscriptionIntent.CREATED, BpmnElementType.RECEIVE_TASK);
    final var correlated =
        record(MessageSubscriptionIntent.CORRELATED, BpmnElementType.RECEIVE_TASK);

    // when
    final var addIds = addHandler.generateIds(created);
    final var removeIds = removeHandler.generateIds(correlated);

    // then — same stable document id (record key) used for write and delete
    assertThat(addIds).containsExactly(String.valueOf(RECORD_KEY));
    assertThat(removeIds).containsExactly(String.valueOf(RECORD_KEY));
  }

  @SuppressWarnings("unchecked")
  private Record<MessageSubscriptionRecordValue> record(
      final MessageSubscriptionIntent intent, final BpmnElementType elementType) {
    final var value =
        ImmutableMessageSubscriptionRecordValue.builder()
            .from(factory.generateObject(MessageSubscriptionRecordValue.class))
            .withElementType(elementType)
            .build();
    return (Record<MessageSubscriptionRecordValue>)
        (Record<? extends RecordValue>)
            factory.generateRecord(
                ValueType.MESSAGE_SUBSCRIPTION,
                r ->
                    r.withKey(RECORD_KEY)
                        .withRecordType(RecordType.EVENT)
                        .withIntent(intent)
                        .withValue(value));
  }

  @SuppressWarnings("unchecked")
  private static Record<MessageSubscriptionRecordValue> cast(
      final Record<? extends RecordValue> record) {
    return (Record<MessageSubscriptionRecordValue>) record;
  }
}
