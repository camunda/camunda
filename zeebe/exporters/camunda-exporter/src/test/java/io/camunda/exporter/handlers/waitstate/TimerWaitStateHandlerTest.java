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
import io.camunda.zeebe.exporter.common.waitstate.transformers.TimerBasedWaitStateTransformer;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ImmutableTimerRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.TimerRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * End-to-end integration test for the timer wait-state handler triple produced by {@link
 * WaitStateHandlerBuilder} with {@link TimerBasedWaitStateTransformer}.
 *
 * <p>Validates: {@code TIMER.CREATED} writes the entry, {@code MIGRATED} upserts it, and {@code
 * TRIGGERED}/{@code CANCELED} remove it. Only intermediate catch event timers with a valid process
 * instance key are tracked.
 */
class TimerWaitStateHandlerTest {

  private static final String INDEX_NAME = "test-wait-state";
  private static final long RECORD_KEY = 999L;

  private final ProtocolFactory factory = new ProtocolFactory();
  private final ObjectMapper objectMapper = new ObjectMapper();

  private WaitStateAddHandler<TimerRecordValue> addHandler;
  private WaitStateUpdateHandler<TimerRecordValue> updateHandler;
  private WaitStateRemoveHandler<TimerRecordValue> removeHandler;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    final var handlers =
        WaitStateHandlerBuilder.of(INDEX_NAME, objectMapper)
            .addTransformer(new TimerBasedWaitStateTransformer())
            .build();

    addHandler =
        (WaitStateAddHandler<TimerRecordValue>)
            handlers.stream()
                .filter(h -> h instanceof WaitStateAddHandler)
                .findFirst()
                .orElseThrow();
    updateHandler =
        (WaitStateUpdateHandler<TimerRecordValue>)
            handlers.stream()
                .filter(h -> h instanceof WaitStateUpdateHandler)
                .findFirst()
                .orElseThrow();
    removeHandler =
        (WaitStateRemoveHandler<TimerRecordValue>)
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
            .addTransformer(new TimerBasedWaitStateTransformer())
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
  void shouldAddHandlerAcceptCreatedForProcessInstanceIntermediateCatchEvent() {
    // given — valid: processInstanceKey set, elementType is INTERMEDIATE_CATCH_EVENT
    final var created =
        timerRecord(TimerIntent.CREATED, 200L, BpmnElementType.INTERMEDIATE_CATCH_EVENT);

    // when / then
    assertThat(addHandler.handlesRecord(created)).isTrue();
  }

  @Test
  void shouldAllHandlersRejectTimerStartEvents() {
    // given — processInstanceKey == -1 means a timer start event subscription (no process instance)
    final var created =
        timerRecord(TimerIntent.CREATED, -1L, BpmnElementType.INTERMEDIATE_CATCH_EVENT);
    final var triggered =
        timerRecord(TimerIntent.TRIGGERED, -1L, BpmnElementType.INTERMEDIATE_CATCH_EVENT);

    // when / then
    assertThat(addHandler.handlesRecord(created)).isFalse();
    assertThat(updateHandler.handlesRecord(created)).isFalse();
    assertThat(removeHandler.handlesRecord(triggered)).isFalse();
  }

  @Test
  void shouldAllHandlersRejectBoundaryTimerEvents() {
    // given — boundary events interrupt the host element; they are not standalone wait states
    final var created = timerRecord(TimerIntent.CREATED, 200L, BpmnElementType.BOUNDARY_EVENT);
    final var triggered = timerRecord(TimerIntent.TRIGGERED, 200L, BpmnElementType.BOUNDARY_EVENT);

    // when / then
    assertThat(addHandler.handlesRecord(created)).isFalse();
    assertThat(updateHandler.handlesRecord(created)).isFalse();
    assertThat(removeHandler.handlesRecord(triggered)).isFalse();
  }

  @Test
  void shouldUpdateHandlerAcceptMigratedAndRejectCreated() {
    // given
    final var migrated =
        timerRecord(TimerIntent.MIGRATED, 200L, BpmnElementType.INTERMEDIATE_CATCH_EVENT);
    final var created =
        timerRecord(TimerIntent.CREATED, 200L, BpmnElementType.INTERMEDIATE_CATCH_EVENT);

    // when / then
    assertThat(updateHandler.handlesRecord(migrated)).isTrue();
    assertThat(addHandler.handlesRecord(migrated)).isFalse();
    assertThat(removeHandler.handlesRecord(migrated)).isFalse();
    assertThat(updateHandler.handlesRecord(created)).isFalse();
  }

  @Test
  void shouldRemoveHandlerAcceptTriggeredAndCanceled() {
    // given
    final var triggered =
        timerRecord(TimerIntent.TRIGGERED, 200L, BpmnElementType.INTERMEDIATE_CATCH_EVENT);
    final var canceled =
        timerRecord(TimerIntent.CANCELED, 200L, BpmnElementType.INTERMEDIATE_CATCH_EVENT);

    // when / then
    assertThat(removeHandler.handlesRecord(triggered)).isTrue();
    assertThat(removeHandler.handlesRecord(canceled)).isTrue();
    assertThat(addHandler.handlesRecord(triggered)).isFalse();
    assertThat(updateHandler.handlesRecord(triggered)).isFalse();
  }

  @Test
  void shouldWriteEntityWithTimerDetails() throws Exception {
    // given
    final var value =
        ImmutableTimerRecordValue.builder()
            .from(factory.generateObject(TimerRecordValue.class))
            .withDueDate(1_000_000L)
            .withRepetitions(2)
            .withElementType(BpmnElementType.INTERMEDIATE_CATCH_EVENT)
            .withTargetElementId("timer-catch")
            .withElementInstanceKey(300L)
            .withProcessInstanceKey(200L)
            .withRootProcessInstanceKey(100L)
            .withBpmnProcessId("my-process")
            .withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .build();
    final var timerRecord =
        cast(
            factory.generateRecord(
                ValueType.TIMER,
                r ->
                    r.withKey(RECORD_KEY)
                        .withRecordType(RecordType.EVENT)
                        .withIntent(TimerIntent.CREATED)
                        .withValue(value)));
    final var entity = addHandler.createNewEntity(String.valueOf(RECORD_KEY));

    // when
    addHandler.updateEntity(timerRecord, entity);

    // then
    assertThat(entity.getElementId()).isEqualTo("timer-catch");
    assertThat(entity.getElementInstanceKey()).isEqualTo(300L);
    assertThat(entity.getProcessInstanceKey()).isEqualTo(200L);
    assertThat(entity.getRootProcessInstanceKey()).isEqualTo(100L);
    assertThat(entity.getWaitStateType()).isEqualTo(WaitStateType.TIMER.name());

    // then — timer-specific details serialised as JSON
    final var details = objectMapper.readTree(entity.getDetails());
    assertThat(details.get("dueDate").longValue()).isEqualTo(1_000_000L);
    assertThat(details.get("repetitions").intValue()).isEqualTo(2);
  }

  @Test
  void shouldBothHandlersUseTheSameDocumentId() {
    // given
    final var created =
        timerRecord(TimerIntent.CREATED, 200L, BpmnElementType.INTERMEDIATE_CATCH_EVENT);
    final var triggered =
        timerRecord(TimerIntent.TRIGGERED, 200L, BpmnElementType.INTERMEDIATE_CATCH_EVENT);

    // when
    final var addIds = addHandler.generateIds(created);
    final var removeIds = removeHandler.generateIds(triggered);

    // then — same stable document id (record key) used for write and delete
    assertThat(addIds).containsExactly(String.valueOf(RECORD_KEY));
    assertThat(removeIds).containsExactly(String.valueOf(RECORD_KEY));
  }

  @SuppressWarnings("unchecked")
  private Record<TimerRecordValue> timerRecord(
      final TimerIntent intent, final long processInstanceKey, final BpmnElementType elementType) {
    final var value =
        ImmutableTimerRecordValue.builder()
            .from(factory.generateObject(TimerRecordValue.class))
            .withProcessInstanceKey(processInstanceKey)
            .withRootProcessInstanceKey(processInstanceKey == -1L ? -1L : 100L)
            .withBpmnProcessId(processInstanceKey == -1L ? "" : "my-process")
            .withElementType(elementType)
            .build();
    return (Record<TimerRecordValue>)
        (Record<? extends RecordValue>)
            factory.generateRecord(
                ValueType.TIMER,
                r ->
                    r.withKey(RECORD_KEY)
                        .withRecordType(RecordType.EVENT)
                        .withIntent(intent)
                        .withValue(value));
  }

  @SuppressWarnings("unchecked")
  private static Record<TimerRecordValue> cast(final Record<? extends RecordValue> record) {
    return (Record<TimerRecordValue>) record;
  }
}
