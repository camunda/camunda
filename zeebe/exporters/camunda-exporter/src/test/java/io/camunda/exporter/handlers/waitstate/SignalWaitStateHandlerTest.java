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
import io.camunda.zeebe.exporter.common.waitstate.transformers.SignalBasedWaitStateTransformer;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.SignalSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ImmutableSignalSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.SignalSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * End-to-end integration test for the signal-subscription wait-state handler triple produced by
 * {@link WaitStateHandlerBuilder} with {@link SignalBasedWaitStateTransformer}.
 *
 * <p>Validates: {@code SIGNAL_SUBSCRIPTION.CREATED} writes the entry, {@code MIGRATED} upserts it,
 * and {@code DELETED} removes it. Only {@link BpmnElementType#INTERMEDIATE_CATCH_EVENT}
 * subscriptions are tracked — start and boundary event subscriptions are skipped.
 */
class SignalWaitStateHandlerTest {

  private static final String INDEX_NAME = "test-wait-state";
  private static final long RECORD_KEY = 999L;

  private final ProtocolFactory factory = new ProtocolFactory();
  private final ObjectMapper objectMapper = new ObjectMapper();

  private WaitStateAddHandler<SignalSubscriptionRecordValue> addHandler;
  private WaitStateUpdateHandler<SignalSubscriptionRecordValue> updateHandler;
  private WaitStateRemoveHandler<SignalSubscriptionRecordValue> removeHandler;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    final var handlers =
        WaitStateHandlerBuilder.of(INDEX_NAME, objectMapper)
            .addTransformer(new SignalBasedWaitStateTransformer())
            .build();

    addHandler =
        (WaitStateAddHandler<SignalSubscriptionRecordValue>)
            handlers.stream()
                .filter(h -> h instanceof WaitStateAddHandler)
                .findFirst()
                .orElseThrow();
    updateHandler =
        (WaitStateUpdateHandler<SignalSubscriptionRecordValue>)
            handlers.stream()
                .filter(h -> h instanceof WaitStateUpdateHandler)
                .findFirst()
                .orElseThrow();
    removeHandler =
        (WaitStateRemoveHandler<SignalSubscriptionRecordValue>)
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
            .addTransformer(new SignalBasedWaitStateTransformer())
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
  void shouldAddHandlerAcceptCreatedForIntermediateCatchEventOnly() {
    // given
    final var catchEventCreated =
        record(SignalSubscriptionIntent.CREATED, BpmnElementType.INTERMEDIATE_CATCH_EVENT);
    final var startEventCreated =
        record(SignalSubscriptionIntent.CREATED, BpmnElementType.START_EVENT);
    final var boundaryEventCreated =
        record(SignalSubscriptionIntent.CREATED, BpmnElementType.BOUNDARY_EVENT);

    // when / then
    assertThat(addHandler.handlesRecord(catchEventCreated)).isTrue();
    assertThat(addHandler.handlesRecord(startEventCreated)).isFalse();
    assertThat(addHandler.handlesRecord(boundaryEventCreated)).isFalse();
  }

  @Test
  void shouldAllHandlersRejectStartAndBoundaryEventSubscriptions() {
    // given — start events have no process instance context; boundary events are interrupts
    final var startDeleted = record(SignalSubscriptionIntent.DELETED, BpmnElementType.START_EVENT);
    final var boundaryDeleted =
        record(SignalSubscriptionIntent.DELETED, BpmnElementType.BOUNDARY_EVENT);
    final var startMigrated =
        record(SignalSubscriptionIntent.MIGRATED, BpmnElementType.START_EVENT);

    // when / then
    assertThat(removeHandler.handlesRecord(startDeleted)).isFalse();
    assertThat(removeHandler.handlesRecord(boundaryDeleted)).isFalse();
    assertThat(updateHandler.handlesRecord(startMigrated)).isFalse();
  }

  @Test
  void shouldUpdateHandlerAcceptMigratedAndRejectCreated() {
    // given
    final var migrated =
        record(SignalSubscriptionIntent.MIGRATED, BpmnElementType.INTERMEDIATE_CATCH_EVENT);
    final var created =
        record(SignalSubscriptionIntent.CREATED, BpmnElementType.INTERMEDIATE_CATCH_EVENT);

    // when / then
    assertThat(updateHandler.handlesRecord(migrated)).isTrue();
    assertThat(addHandler.handlesRecord(migrated)).isFalse();
    assertThat(removeHandler.handlesRecord(migrated)).isFalse();
    assertThat(updateHandler.handlesRecord(created)).isFalse();
  }

  @Test
  void shouldRemoveHandlerAcceptDeletedOnly() {
    // given — signal subscriptions have only one removal intent (no "correlation" concept)
    final var deleted =
        record(SignalSubscriptionIntent.DELETED, BpmnElementType.INTERMEDIATE_CATCH_EVENT);
    final var created =
        record(SignalSubscriptionIntent.CREATED, BpmnElementType.INTERMEDIATE_CATCH_EVENT);

    // when / then
    assertThat(removeHandler.handlesRecord(deleted)).isTrue();
    assertThat(addHandler.handlesRecord(deleted)).isFalse();
    assertThat(updateHandler.handlesRecord(deleted)).isFalse();
    assertThat(removeHandler.handlesRecord(created)).isFalse();
  }

  @Test
  void shouldWriteEntityWithSignalDetails() throws Exception {
    // given
    final var value =
        ImmutableSignalSubscriptionRecordValue.builder()
            .from(factory.generateObject(SignalSubscriptionRecordValue.class))
            .withSignalName("order-confirmed")
            .withBpmnElementType(BpmnElementType.INTERMEDIATE_CATCH_EVENT)
            .withCatchEventId("signal-catch")
            .withCatchEventInstanceKey(300L)
            .withProcessInstanceKey(200L)
            .withRootProcessInstanceKey(100L)
            .withBpmnProcessId("my-process")
            .withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .build();
    final var signalRecord =
        cast(
            factory.generateRecord(
                ValueType.SIGNAL_SUBSCRIPTION,
                r ->
                    r.withKey(RECORD_KEY)
                        .withRecordType(RecordType.EVENT)
                        .withIntent(SignalSubscriptionIntent.CREATED)
                        .withValue(value)));
    final var entity = addHandler.createNewEntity(String.valueOf(RECORD_KEY));

    // when
    addHandler.updateEntity(signalRecord, entity);

    // then
    assertThat(entity.getElementId()).isEqualTo("signal-catch");
    assertThat(entity.getElementInstanceKey()).isEqualTo(300L);
    assertThat(entity.getProcessInstanceKey()).isEqualTo(200L);
    assertThat(entity.getRootProcessInstanceKey()).isEqualTo(100L);
    assertThat(entity.getWaitStateType()).isEqualTo(WaitStateType.SIGNAL.name());

    // then — signal-specific details serialised as JSON
    final var details = objectMapper.readTree(entity.getDetails());
    assertThat(details.get("signalName").textValue()).isEqualTo("order-confirmed");
  }

  @Test
  void shouldBothHandlersUseTheSameDocumentId() {
    // given
    final var created =
        record(SignalSubscriptionIntent.CREATED, BpmnElementType.INTERMEDIATE_CATCH_EVENT);
    final var deleted =
        record(SignalSubscriptionIntent.DELETED, BpmnElementType.INTERMEDIATE_CATCH_EVENT);

    // when
    final var addIds = addHandler.generateIds(created);
    final var removeIds = removeHandler.generateIds(deleted);

    // then — same stable document id (record key) used for write and delete
    assertThat(addIds).containsExactly(String.valueOf(RECORD_KEY));
    assertThat(removeIds).containsExactly(String.valueOf(RECORD_KEY));
  }

  @SuppressWarnings("unchecked")
  private Record<SignalSubscriptionRecordValue> record(
      final SignalSubscriptionIntent intent, final BpmnElementType elementType) {
    final var value =
        ImmutableSignalSubscriptionRecordValue.builder()
            .from(factory.generateObject(SignalSubscriptionRecordValue.class))
            .withProcessInstanceKey(200L)
            .withRootProcessInstanceKey(100L)
            .withBpmnProcessId("my-process")
            .withBpmnElementType(elementType)
            .build();
    return (Record<SignalSubscriptionRecordValue>)
        (Record<? extends RecordValue>)
            factory.generateRecord(
                ValueType.SIGNAL_SUBSCRIPTION,
                r ->
                    r.withKey(RECORD_KEY)
                        .withRecordType(RecordType.EVENT)
                        .withIntent(intent)
                        .withValue(value));
  }

  @SuppressWarnings("unchecked")
  private static Record<SignalSubscriptionRecordValue> cast(
      final Record<? extends RecordValue> record) {
    return (Record<SignalSubscriptionRecordValue>) record;
  }
}
