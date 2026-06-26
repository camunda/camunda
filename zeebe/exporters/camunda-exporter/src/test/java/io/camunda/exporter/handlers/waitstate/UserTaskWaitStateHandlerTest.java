/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.waitstate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.descriptors.template.WaitStateTemplate;
import io.camunda.webapps.schema.entities.waitstate.WaitStateEntity;
import io.camunda.zeebe.exporter.common.waitstate.WaitStateEntry.WaitStateType;
import io.camunda.zeebe.exporter.common.waitstate.transformers.UserTaskBasedWaitStateTransformer;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ImmutableUserTaskRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.UserTaskRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * End-to-end integration test for the native user task wait-state handler triple produced by {@link
 * WaitStateHandlerBuilder} with {@link UserTaskBasedWaitStateTransformer}.
 *
 * <p>Validates the full lifecycle: a {@code USER_TASK.CREATED} event writes the wait-state entry,
 * {@code MIGRATED}/{@code UPDATED} upsert it, and {@code COMPLETED}/{@code CANCELED} remove it
 * using the same stable document id (the userTaskKey).
 */
class UserTaskWaitStateHandlerTest {

  private static final String INDEX_NAME = "test-wait-state";
  private static final long USER_TASK_KEY = 999L;

  private final ProtocolFactory factory = new ProtocolFactory();
  private final ObjectMapper objectMapper = new ObjectMapper();

  private WaitStateAddHandler<UserTaskRecordValue> addHandler;
  private WaitStateUpdateHandler<UserTaskRecordValue> updateHandler;
  private WaitStateRemoveHandler<UserTaskRecordValue> removeHandler;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    final var handlers =
        WaitStateHandlerBuilder.of(INDEX_NAME, objectMapper)
            .addTransformer(new UserTaskBasedWaitStateTransformer())
            .build();

    addHandler =
        (WaitStateAddHandler<UserTaskRecordValue>)
            handlers.stream()
                .filter(h -> h instanceof WaitStateAddHandler)
                .findFirst()
                .orElseThrow();
    updateHandler =
        (WaitStateUpdateHandler<UserTaskRecordValue>)
            handlers.stream()
                .filter(h -> h instanceof WaitStateUpdateHandler)
                .findFirst()
                .orElseThrow();
    removeHandler =
        (WaitStateRemoveHandler<UserTaskRecordValue>)
            handlers.stream()
                .filter(h -> h instanceof WaitStateRemoveHandler)
                .findFirst()
                .orElseThrow();
  }

  @Test
  void shouldAddHandlerAcceptCreatedRecordOnly() {
    // given
    final var created = userTaskRecord(UserTaskIntent.CREATED);
    final var completed = userTaskRecord(UserTaskIntent.COMPLETED);

    // when / then
    assertThat(addHandler.handlesRecord(created)).isTrue();
    assertThat(addHandler.handlesRecord(completed)).isFalse();
    assertThat(removeHandler.handlesRecord(created)).isFalse();
    assertThat(removeHandler.handlesRecord(completed)).isTrue();
  }

  @Test
  void shouldUpdateHandlerAcceptMigratedAndUpdatedNotAddHandler() {
    // given
    final var migrated = userTaskRecord(UserTaskIntent.MIGRATED);
    final var updated = userTaskRecord(UserTaskIntent.UPDATED);

    // when / then — MIGRATED and UPDATED are update intents, handled by updateHandler, not
    // addHandler
    assertThat(addHandler.handlesRecord(migrated)).isFalse();
    assertThat(addHandler.handlesRecord(updated)).isFalse();
    assertThat(removeHandler.handlesRecord(migrated)).isFalse();
    assertThat(removeHandler.handlesRecord(updated)).isFalse();
    assertThat(updateHandler.handlesRecord(migrated)).isTrue();
    assertThat(updateHandler.handlesRecord(updated)).isTrue();
  }

  @Test
  void shouldBothHandlersUseTheSameDocumentId() {
    // given
    final var created = userTaskRecord(UserTaskIntent.CREATED);
    final var completed = userTaskRecord(UserTaskIntent.COMPLETED);

    // when
    final var addIds = addHandler.generateIds(created);
    final var removeIds = removeHandler.generateIds(completed);

    // then — same stable document id (userTaskKey) used for write and delete
    assertThat(addIds).containsExactly(String.valueOf(USER_TASK_KEY));
    assertThat(removeIds).containsExactly(String.valueOf(USER_TASK_KEY));
  }

  @Test
  void shouldWriteFullyPopulatedEntityOnUserTaskCreated() throws Exception {
    // given
    final var record = userTaskRecordWithDetails(UserTaskIntent.CREATED, "approve-task");
    final var entity = addHandler.createNewEntity(String.valueOf(USER_TASK_KEY));

    // when
    addHandler.updateEntity(record, entity);

    // then
    assertThat(entity.getRootProcessInstanceKey()).isEqualTo(100L);
    assertThat(entity.getProcessInstanceKey()).isEqualTo(200L);
    assertThat(entity.getElementInstanceKey()).isEqualTo(300L);
    assertThat(entity.getElementId()).isEqualTo("approve-task");
    assertThat(entity.getElementType()).isEqualTo(BpmnElementType.USER_TASK.name());
    assertThat(entity.getWaitStateType()).isEqualTo(WaitStateType.USER_TASK.name());
    assertThat(entity.getTenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    // then — details serialised as JSON with all fields
    final var details = objectMapper.readTree(entity.getDetails());
    assertThat(details.get("taskKey").longValue()).isEqualTo(USER_TASK_KEY);
    assertThat(details.get("dueDate").textValue()).isEqualTo("2026-10-13T10:00:00+01:00");
  }

  @Test
  void shouldUpdateEntityElementIdOnUserTaskMigrated() throws Exception {
    // given — a record with the post-migration elementId
    final var record = userTaskRecordWithDetails(UserTaskIntent.MIGRATED, "task-after-migration");
    final var entity = updateHandler.createNewEntity(String.valueOf(USER_TASK_KEY));

    // when
    updateHandler.updateEntity(record, entity);

    // then — entity reflects the post-migration element id (update handler sends ELEMENT_ID +
    // DETAILS only)
    assertThat(entity.getElementId()).isEqualTo("task-after-migration");
    assertThat(entity.getElementType()).isEqualTo(BpmnElementType.USER_TASK.name());
    assertThat(entity.getProcessInstanceKey()).isEqualTo(200L);
    assertThat(entity.getWaitStateType()).isEqualTo(WaitStateType.USER_TASK.name());
    final var details = objectMapper.readTree(entity.getDetails());
    assertThat(details.get("taskKey").longValue()).isEqualTo(USER_TASK_KEY);
  }

  @Test
  void shouldFlushAddEntityToIndex() throws PersistenceException {
    // given
    final var entity = new WaitStateEntity().setId(String.valueOf(USER_TASK_KEY));
    final var batchRequest = mock(BatchRequest.class);

    // when
    addHandler.flush(entity, batchRequest);

    // then
    verify(batchRequest).add(eq(INDEX_NAME), eq(entity));
  }

  @Test
  void shouldFlushRemoveDeleteFromIndexBySameId() throws PersistenceException {
    // given
    final var entity = new WaitStateEntity().setId(String.valueOf(USER_TASK_KEY));
    final var batchRequest = mock(BatchRequest.class);

    // when
    removeHandler.flush(entity, batchRequest);

    // then
    verify(batchRequest).delete(INDEX_NAME, String.valueOf(USER_TASK_KEY));
  }

  @Test
  void shouldRemoveHandlerAlsoHandleCanceledEvent() {
    // given
    final var canceled = userTaskRecord(UserTaskIntent.CANCELED);

    // when / then
    assertThat(removeHandler.handlesRecord(canceled)).isTrue();
    assertThat(addHandler.handlesRecord(canceled)).isFalse();
  }

  @Test
  void shouldUpdateHandlerHandleMigratedAndUpdated() {
    // given
    final var migrated = userTaskRecord(UserTaskIntent.MIGRATED);
    final var updated = userTaskRecord(UserTaskIntent.UPDATED);
    final var created = userTaskRecord(UserTaskIntent.CREATED);

    // when / then — MIGRATED and UPDATED are update intents; CREATED is not
    assertThat(updateHandler.handlesRecord(migrated)).isTrue();
    assertThat(updateHandler.handlesRecord(updated)).isTrue();
    assertThat(updateHandler.handlesRecord(created)).isFalse();
  }

  @Test
  void shouldUpdateHandlerSkipsElementIdWhenNull() throws PersistenceException {
    // given — elementId null means no migration, only task metadata changed
    final var id = String.valueOf(USER_TASK_KEY);
    final var entity = new WaitStateEntity().setId(id).setDetails("{\"taskKey\":999}");
    final var batchRequest = mock(BatchRequest.class);

    // when
    updateHandler.flush(entity, batchRequest);

    // then — only DETAILS in the update map; ELEMENT_ID is skipped
    verify(batchRequest)
        .upsert(
            eq(INDEX_NAME),
            eq(id),
            eq(entity),
            argThat(map -> map.containsKey(WaitStateTemplate.DETAILS) && map.size() == 1));
  }

  @Test
  void shouldUpdateHandlerIncludesElementIdWhenPresentForMigration() throws PersistenceException {
    // given — MIGRATED: elementId and bpmnProcessId are populated with values from the target
    // process definition (bpmnProcessId changes on cross-process migration)
    final var id = String.valueOf(USER_TASK_KEY);
    final var entity =
        new WaitStateEntity()
            .setId(id)
            .setElementId("task-after-migration")
            .setBpmnProcessId("target-process")
            .setDetails("{\"taskKey\":999}");
    final var batchRequest = mock(BatchRequest.class);

    // when
    updateHandler.flush(entity, batchRequest);

    // then — ELEMENT_ID, BPMN_PROCESS_ID and DETAILS are all sent
    verify(batchRequest)
        .upsert(
            eq(INDEX_NAME),
            eq(id),
            eq(entity),
            argThat(
                map ->
                    map.containsKey(WaitStateTemplate.ELEMENT_ID)
                        && map.containsKey(WaitStateTemplate.BPMN_PROCESS_ID)
                        && map.containsKey(WaitStateTemplate.DETAILS)
                        && map.size() == 3));
  }

  @Test
  void shouldBuilderProduceExactlyThreeHandlers() {
    // when
    final var handlers =
        WaitStateHandlerBuilder.of(INDEX_NAME, objectMapper)
            .addTransformer(new UserTaskBasedWaitStateTransformer())
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

  private Record<UserTaskRecordValue> userTaskRecordWithDetails(
      final UserTaskIntent intent, final String elementId) {
    final var value =
        ImmutableUserTaskRecordValue.builder()
            .from(factory.generateObject(UserTaskRecordValue.class))
            .withUserTaskKey(USER_TASK_KEY)
            .withDueDate("2026-10-13T10:00:00+01:00")
            .withElementId(elementId)
            .withElementInstanceKey(300L)
            .withProcessInstanceKey(200L)
            .withRootProcessInstanceKey(100L)
            .withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .build();
    return cast(
        factory.generateRecord(
            ValueType.USER_TASK,
            r ->
                r.withKey(USER_TASK_KEY)
                    .withRecordType(RecordType.EVENT)
                    .withIntent(intent)
                    .withValue(value)));
  }

  @SuppressWarnings("unchecked")
  private Record<UserTaskRecordValue> userTaskRecord(final UserTaskIntent intent) {
    return (Record<UserTaskRecordValue>)
        (Record<? extends RecordValue>)
            factory.generateRecord(
                ValueType.USER_TASK,
                r -> r.withKey(USER_TASK_KEY).withRecordType(RecordType.EVENT).withIntent(intent));
  }

  @SuppressWarnings("unchecked")
  private static Record<UserTaskRecordValue> cast(final Record<? extends RecordValue> record) {
    return (Record<UserTaskRecordValue>) record;
  }
}
