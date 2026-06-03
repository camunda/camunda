/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.waitstate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.exporter.exceptions.PersistenceException;
import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.waitstate.WaitStateEntity;
import io.camunda.zeebe.exporter.common.waitstate.WaitStateEntry.WaitStateType;
import io.camunda.zeebe.exporter.common.waitstate.transformers.JobBasedWaitStateTransformer;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ImmutableJobRecordValue;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.JobListenerEventType;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * End-to-end integration test for the job wait-state handler pair produced by {@link
 * WaitStateHandlerBuilder} with {@link JobBasedWaitStateTransformer}.
 *
 * <p>Validates the full lifecycle: a {@code JOB.CREATED} event writes the wait-state entry, and a
 * subsequent {@code JOB.COMPLETED} or {@code JOB.CANCELED} event removes it using the same stable
 * document id (the jobKey).
 */
class JobWaitStateHandlerTest {

  private static final String INDEX_NAME = "test-wait-state";
  private static final long JOB_KEY = 999L;

  private final ProtocolFactory factory = new ProtocolFactory();
  private final ObjectMapper objectMapper = new ObjectMapper();

  private WaitStateAddHandler<JobRecordValue> addHandler;
  private WaitStateRemoveHandler<JobRecordValue> removeHandler;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    final var handlers =
        WaitStateHandlerBuilder.of(INDEX_NAME, objectMapper)
            .addTransformer(new JobBasedWaitStateTransformer())
            .build();

    addHandler =
        (WaitStateAddHandler<JobRecordValue>)
            handlers.stream()
                .filter(h -> h instanceof WaitStateAddHandler)
                .findFirst()
                .orElseThrow();
    removeHandler =
        (WaitStateRemoveHandler<JobRecordValue>)
            handlers.stream()
                .filter(h -> h instanceof WaitStateRemoveHandler)
                .findFirst()
                .orElseThrow();
  }

  @Test
  void shouldAddHandlerHandleCreatedAndRemoveHandlerNotHandle() {
    // given
    final var created = jobRecord(JobIntent.CREATED);
    final var completed = jobRecord(JobIntent.COMPLETED);

    // when / then
    assertThat(addHandler.handlesRecord(created)).isTrue();
    assertThat(addHandler.handlesRecord(completed)).isFalse();
    assertThat(removeHandler.handlesRecord(created)).isFalse();
    assertThat(removeHandler.handlesRecord(completed)).isTrue();
  }

  @Test
  void shouldBothHandlersUseTheSameDocumentId() {
    // given
    final var created = jobRecord(JobIntent.CREATED);
    final var completed = jobRecord(JobIntent.COMPLETED);

    // when
    final var addIds = addHandler.generateIds(created);
    final var removeIds = removeHandler.generateIds(completed);

    // then — same stable document id (jobKey) used for write and delete
    assertThat(addIds).containsExactly(String.valueOf(JOB_KEY));
    assertThat(removeIds).containsExactly(String.valueOf(JOB_KEY));
  }

  @Test
  void shouldWriteFullyPopulatedEntityOnJobCreated() throws Exception {
    // given
    final var jobValue =
        ImmutableJobRecordValue.builder()
            .from(factory.generateObject(JobRecordValue.class))
            .withType("payment-service")
            .withJobKind(JobKind.BPMN_ELEMENT)
            .withJobListenerEventType(JobListenerEventType.UNSPECIFIED)
            .withRetries(3)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withElementId("task-payment")
            .withElementInstanceKey(300L)
            .withProcessInstanceKey(200L)
            .withRootProcessInstanceKey(100L)
            .withTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
            .build();
    final var record =
        cast(
            factory.generateRecord(
                ValueType.JOB,
                r ->
                    r.withKey(JOB_KEY)
                        .withRecordType(RecordType.EVENT)
                        .withIntent(JobIntent.CREATED)
                        .withValue(jobValue)));
    final var entity = addHandler.createNewEntity(String.valueOf(JOB_KEY));

    // when
    addHandler.updateEntity(record, entity);

    // then
    assertThat(entity.getRootProcessInstanceKey()).isEqualTo(100L);
    assertThat(entity.getProcessInstanceKey()).isEqualTo(200L);
    assertThat(entity.getElementInstanceKey()).isEqualTo(300L);
    assertThat(entity.getElementId()).isEqualTo("task-payment");
    assertThat(entity.getElementType()).isEqualTo(BpmnElementType.SERVICE_TASK.name());
    assertThat(entity.getWaitStateType()).isEqualTo(WaitStateType.JOB.name());
    assertThat(entity.getTenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    // then — details serialised as JSON with all fields
    final var details = objectMapper.readTree(entity.getDetails());
    assertThat(details.get("jobKey").longValue()).isEqualTo(JOB_KEY);
    assertThat(details.get("jobType").textValue()).isEqualTo("payment-service");
    assertThat(details.get("jobKind").textValue()).isEqualTo(JobKind.BPMN_ELEMENT.name());
    assertThat(details.get("listenerEventType").textValue())
        .isEqualTo(JobListenerEventType.UNSPECIFIED.name());
    assertThat(details.get("retries").intValue()).isEqualTo(3);
  }

  @Test
  void shouldFlushAddEntityToIndex() throws PersistenceException {
    // given
    final var entity = new WaitStateEntity().setId(String.valueOf(JOB_KEY));
    final var batchRequest = mock(BatchRequest.class);

    // when
    addHandler.flush(entity, batchRequest);

    // then
    verify(batchRequest).add(eq(INDEX_NAME), eq(entity));
  }

  @Test
  void shouldFlushRemoveDeleteFromIndexBySameId() throws PersistenceException {
    // given
    final var entity = new WaitStateEntity().setId(String.valueOf(JOB_KEY));
    final var batchRequest = mock(BatchRequest.class);

    // when
    removeHandler.flush(entity, batchRequest);

    // then
    verify(batchRequest).delete(INDEX_NAME, String.valueOf(JOB_KEY));
  }

  @Test
  void shouldRemoveHandlerAlsoHandleCanceledEvent() {
    // given
    final var canceled = jobRecord(JobIntent.CANCELED);

    // when / then
    assertThat(removeHandler.handlesRecord(canceled)).isTrue();
    assertThat(addHandler.handlesRecord(canceled)).isFalse();
  }

  @Test
  void shouldBuilderProduceExactlyTwoHandlers() {
    final var handlers =
        WaitStateHandlerBuilder.of(INDEX_NAME, objectMapper)
            .addTransformer(new JobBasedWaitStateTransformer())
            .build();

    assertThat(handlers).hasSize(2);
    assertThat(handlers.stream().filter(h -> h instanceof WaitStateAddHandler).count())
        .isEqualTo(1);
    assertThat(handlers.stream().filter(h -> h instanceof WaitStateRemoveHandler).count())
        .isEqualTo(1);
  }

  @SuppressWarnings("unchecked")
  private Record<JobRecordValue> jobRecord(final JobIntent intent) {
    return (Record<JobRecordValue>)
        (Record<? extends RecordValue>)
            factory.generateRecord(
                ValueType.JOB,
                r -> r.withKey(JOB_KEY).withRecordType(RecordType.EVENT).withIntent(intent));
  }

  @SuppressWarnings("unchecked")
  private static Record<JobRecordValue> cast(final Record<? extends RecordValue> record) {
    return (Record<JobRecordValue>) record;
  }
}
