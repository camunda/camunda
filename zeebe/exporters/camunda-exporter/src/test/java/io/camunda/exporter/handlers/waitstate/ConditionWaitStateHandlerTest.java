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
import io.camunda.zeebe.exporter.common.waitstate.transformers.ConditionBasedWaitStateTransformer;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ConditionalSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ConditionalSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableConditionalSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * End-to-end integration test for the conditional-subscription wait-state handler triple produced
 * by {@link WaitStateHandlerBuilder} with {@link ConditionBasedWaitStateTransformer}.
 *
 * <p>Validates: {@code CREATED} writes the entry, {@code MIGRATED} upserts it, {@code DELETED}
 * removes it, and {@code TRIGGERED} removes it only when the subscription is interrupting. Only
 * {@link BpmnElementType#INTERMEDIATE_CATCH_EVENT} subscriptions with a valid process-instance
 * context are tracked.
 */
class ConditionWaitStateHandlerTest {

  private static final String INDEX_NAME = "test-wait-state";
  private static final long RECORD_KEY = 999L;

  private final ProtocolFactory factory = new ProtocolFactory();
  private final ObjectMapper objectMapper = new ObjectMapper();

  private WaitStateAddHandler<ConditionalSubscriptionRecordValue> addHandler;
  private WaitStateUpdateHandler<ConditionalSubscriptionRecordValue> updateHandler;
  private WaitStateRemoveHandler<ConditionalSubscriptionRecordValue> removeHandler;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    final var handlers =
        WaitStateHandlerBuilder.of(INDEX_NAME, objectMapper)
            .addTransformer(new ConditionBasedWaitStateTransformer())
            .build();

    addHandler =
        (WaitStateAddHandler<ConditionalSubscriptionRecordValue>)
            handlers.stream()
                .filter(h -> h instanceof WaitStateAddHandler)
                .findFirst()
                .orElseThrow();
    updateHandler =
        (WaitStateUpdateHandler<ConditionalSubscriptionRecordValue>)
            handlers.stream()
                .filter(h -> h instanceof WaitStateUpdateHandler)
                .findFirst()
                .orElseThrow();
    removeHandler =
        (WaitStateRemoveHandler<ConditionalSubscriptionRecordValue>)
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
            .addTransformer(new ConditionBasedWaitStateTransformer())
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
  void shouldAddHandlerAcceptCreatedForIntermediateCatchEvent() {
    // given
    final var created =
        record(ConditionalSubscriptionIntent.CREATED, BpmnElementType.INTERMEDIATE_CATCH_EVENT);

    // when / then
    assertThat(addHandler.handlesRecord(created)).isTrue();
    assertThat(updateHandler.handlesRecord(created)).isFalse();
    assertThat(removeHandler.handlesRecord(created)).isFalse();
  }

  @Test
  void shouldAllHandlersRejectSubscriptionsWithoutProcessInstanceContext() {
    // given — processInstanceKey == -1 means root start event with no instance context
    final var created =
        record(
            ConditionalSubscriptionIntent.CREATED,
            BpmnElementType.INTERMEDIATE_CATCH_EVENT,
            -1L,
            100L);
    final var deleted =
        record(
            ConditionalSubscriptionIntent.DELETED,
            BpmnElementType.INTERMEDIATE_CATCH_EVENT,
            -1L,
            100L);

    // when / then
    assertThat(addHandler.handlesRecord(created)).isFalse();
    assertThat(removeHandler.handlesRecord(deleted)).isFalse();
  }

  @Test
  void shouldAllHandlersRejectBoundaryEventSubscriptions() {
    // given — boundary events are not tracked as wait states
    final var created =
        record(ConditionalSubscriptionIntent.CREATED, BpmnElementType.BOUNDARY_EVENT);
    final var deleted =
        record(ConditionalSubscriptionIntent.DELETED, BpmnElementType.BOUNDARY_EVENT);

    // when / then
    assertThat(addHandler.handlesRecord(created)).isFalse();
    assertThat(removeHandler.handlesRecord(deleted)).isFalse();
  }

  @Test
  void shouldAllHandlersRejectStartEventSubscriptions() {
    // given — start event subscriptions (event subprocess) are not tracked
    final var created = record(ConditionalSubscriptionIntent.CREATED, BpmnElementType.START_EVENT);
    final var deleted = record(ConditionalSubscriptionIntent.DELETED, BpmnElementType.START_EVENT);

    // when / then
    assertThat(addHandler.handlesRecord(created)).isFalse();
    assertThat(removeHandler.handlesRecord(deleted)).isFalse();
  }

  @Test
  void shouldUpdateHandlerAcceptMigratedAndRejectCreated() {
    // given
    final var migrated =
        record(ConditionalSubscriptionIntent.MIGRATED, BpmnElementType.INTERMEDIATE_CATCH_EVENT);
    final var created =
        record(ConditionalSubscriptionIntent.CREATED, BpmnElementType.INTERMEDIATE_CATCH_EVENT);

    // when / then
    assertThat(updateHandler.handlesRecord(migrated)).isTrue();
    assertThat(addHandler.handlesRecord(migrated)).isFalse();
    assertThat(removeHandler.handlesRecord(migrated)).isFalse();
    assertThat(updateHandler.handlesRecord(created)).isFalse();
  }

  @Test
  void shouldRemoveHandlerAcceptDeletedIntent() {
    // given
    final var deleted =
        record(ConditionalSubscriptionIntent.DELETED, BpmnElementType.INTERMEDIATE_CATCH_EVENT);

    // when / then
    assertThat(removeHandler.handlesRecord(deleted)).isTrue();
    assertThat(addHandler.handlesRecord(deleted)).isFalse();
    assertThat(updateHandler.handlesRecord(deleted)).isFalse();
  }

  @Test
  void shouldRemoveHandlerAcceptTriggeredOnlyWhenInterrupting() {
    // given
    final var triggeredInterrupting =
        record(
            ConditionalSubscriptionIntent.TRIGGERED,
            BpmnElementType.INTERMEDIATE_CATCH_EVENT,
            true);
    final var triggeredNonInterrupting =
        record(
            ConditionalSubscriptionIntent.TRIGGERED,
            BpmnElementType.INTERMEDIATE_CATCH_EVENT,
            false);

    // when / then — TRIGGERED removes only when the subscription is interrupting
    assertThat(removeHandler.handlesRecord(triggeredInterrupting)).isTrue();
    assertThat(removeHandler.handlesRecord(triggeredNonInterrupting)).isFalse();
  }

  @Test
  void shouldWriteEntityWithConditionDetails() throws Exception {
    // given
    final var value =
        ImmutableConditionalSubscriptionRecordValue.builder()
            .from(factory.generateObject(ConditionalSubscriptionRecordValue.class))
            .withCondition("= x > 5")
            .withVariableEvents(List.of("create", "update"))
            .withElementType(BpmnElementType.INTERMEDIATE_CATCH_EVENT)
            .withCatchEventId("catch-condition")
            .withElementInstanceKey(300L)
            .withProcessInstanceKey(200L)
            .withRootProcessInstanceKey(100L)
            .withBpmnProcessId("test-process")
            .withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .build();
    final var conditionRecord =
        cast(
            factory.generateRecord(
                ValueType.CONDITIONAL_SUBSCRIPTION,
                r ->
                    r.withKey(RECORD_KEY)
                        .withRecordType(RecordType.EVENT)
                        .withIntent(ConditionalSubscriptionIntent.CREATED)
                        .withValue(value)));
    final var entity = addHandler.createNewEntity(String.valueOf(RECORD_KEY));

    // when
    addHandler.updateEntity(conditionRecord, entity);

    // then
    assertThat(entity.getElementId()).isEqualTo("catch-condition");
    assertThat(entity.getElementInstanceKey()).isEqualTo(300L);
    assertThat(entity.getProcessInstanceKey()).isEqualTo(200L);
    assertThat(entity.getRootProcessInstanceKey()).isEqualTo(100L);
    assertThat(entity.getWaitStateType()).isEqualTo(WaitStateType.CONDITION.name());

    // then — condition-specific details serialised as JSON
    final var details = objectMapper.readTree(entity.getDetails());
    assertThat(details.get("expression").textValue()).isEqualTo("= x > 5");
    assertThat(details.get("events").isArray()).isTrue();
    assertThat(details.get("events").get(0).textValue()).isEqualTo("create");
    assertThat(details.get("events").get(1).textValue()).isEqualTo("update");
  }

  @Test
  void shouldNormalizeEmptyVariableEventsToCreateAndUpdate() throws Exception {
    // given — empty variableEvents means "all events" in the engine; transformer normalises to
    // explicit list
    final var value =
        ImmutableConditionalSubscriptionRecordValue.builder()
            .from(factory.generateObject(ConditionalSubscriptionRecordValue.class))
            .withCondition("= active")
            .withVariableEvents(List.of())
            .withElementType(BpmnElementType.INTERMEDIATE_CATCH_EVENT)
            .withCatchEventId("catch-condition")
            .withElementInstanceKey(300L)
            .withProcessInstanceKey(200L)
            .withRootProcessInstanceKey(100L)
            .withBpmnProcessId("test-process")
            .withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .build();
    final var conditionRecord =
        cast(
            factory.generateRecord(
                ValueType.CONDITIONAL_SUBSCRIPTION,
                r ->
                    r.withKey(RECORD_KEY)
                        .withRecordType(RecordType.EVENT)
                        .withIntent(ConditionalSubscriptionIntent.CREATED)
                        .withValue(value)));
    final var entity = addHandler.createNewEntity(String.valueOf(RECORD_KEY));

    // when
    addHandler.updateEntity(conditionRecord, entity);

    // then — empty variableEvents normalised to ["create", "update"]
    final var details = objectMapper.readTree(entity.getDetails());
    assertThat(details.get("events").size()).isEqualTo(2);
    assertThat(details.get("events").get(0).textValue()).isEqualTo("create");
    assertThat(details.get("events").get(1).textValue()).isEqualTo("update");
  }

  @Test
  void shouldBothHandlersUseTheSameDocumentId() {
    // given
    final var created =
        record(ConditionalSubscriptionIntent.CREATED, BpmnElementType.INTERMEDIATE_CATCH_EVENT);
    final var deleted =
        record(ConditionalSubscriptionIntent.DELETED, BpmnElementType.INTERMEDIATE_CATCH_EVENT);

    // when
    final var addIds = addHandler.generateIds(created);
    final var removeIds = removeHandler.generateIds(deleted);

    // then — same stable document id (record key) used for write and delete
    assertThat(addIds).containsExactly(String.valueOf(RECORD_KEY));
    assertThat(removeIds).containsExactly(String.valueOf(RECORD_KEY));
  }

  @SuppressWarnings("unchecked")
  private Record<ConditionalSubscriptionRecordValue> record(
      final ConditionalSubscriptionIntent intent, final BpmnElementType elementType) {
    return record(intent, elementType, 200L, 100L);
  }

  @SuppressWarnings("unchecked")
  private Record<ConditionalSubscriptionRecordValue> record(
      final ConditionalSubscriptionIntent intent,
      final BpmnElementType elementType,
      final long processInstanceKey,
      final long rootProcessInstanceKey) {
    final var value =
        ImmutableConditionalSubscriptionRecordValue.builder()
            .from(factory.generateObject(ConditionalSubscriptionRecordValue.class))
            .withElementType(elementType)
            .withProcessInstanceKey(processInstanceKey)
            .withRootProcessInstanceKey(rootProcessInstanceKey)
            .withBpmnProcessId(processInstanceKey == -1L ? "" : "test-process")
            .build();
    return (Record<ConditionalSubscriptionRecordValue>)
        (Record<? extends RecordValue>)
            factory.generateRecord(
                ValueType.CONDITIONAL_SUBSCRIPTION,
                r ->
                    r.withKey(RECORD_KEY)
                        .withRecordType(RecordType.EVENT)
                        .withIntent(intent)
                        .withValue(value));
  }

  @SuppressWarnings("unchecked")
  private Record<ConditionalSubscriptionRecordValue> record(
      final ConditionalSubscriptionIntent intent,
      final BpmnElementType elementType,
      final boolean interrupting) {
    final var value =
        ImmutableConditionalSubscriptionRecordValue.builder()
            .from(factory.generateObject(ConditionalSubscriptionRecordValue.class))
            .withElementType(elementType)
            .withProcessInstanceKey(200L)
            .withRootProcessInstanceKey(100L)
            .withBpmnProcessId("test-process")
            .withInterrupting(interrupting)
            .build();
    return (Record<ConditionalSubscriptionRecordValue>)
        (Record<? extends RecordValue>)
            factory.generateRecord(
                ValueType.CONDITIONAL_SUBSCRIPTION,
                r ->
                    r.withKey(RECORD_KEY)
                        .withRecordType(RecordType.EVENT)
                        .withIntent(intent)
                        .withValue(value));
  }

  @SuppressWarnings("unchecked")
  private static Record<ConditionalSubscriptionRecordValue> cast(
      final Record<? extends RecordValue> record) {
    return (Record<ConditionalSubscriptionRecordValue>) record;
  }
}
