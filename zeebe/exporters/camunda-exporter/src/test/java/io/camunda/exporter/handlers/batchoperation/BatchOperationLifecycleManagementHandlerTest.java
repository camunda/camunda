/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.batchoperation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.camunda.exporter.store.BatchRequest;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity.BatchOperationState;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationError;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationErrorType;
import io.camunda.zeebe.protocol.record.value.BatchOperationLifecycleManagementRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableBatchOperationLifecycleManagementRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import io.camunda.zeebe.util.DateUtil;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BatchOperationLifecycleManagementHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();
  private final String indexName = "test-index";
  private final BatchOperationLifecycleManagementHandler handler =
      new BatchOperationLifecycleManagementHandler(indexName);

  @Test
  void shouldHandleExportableIntents() {
    // given
    final Record<BatchOperationLifecycleManagementRecordValue> canceledRecord =
        factory.generateRecordWithIntent(
            ValueType.BATCH_OPERATION_LIFECYCLE_MANAGEMENT, BatchOperationIntent.CANCELED);
    final Record<BatchOperationLifecycleManagementRecordValue> suspendedRecord =
        factory.generateRecordWithIntent(
            ValueType.BATCH_OPERATION_LIFECYCLE_MANAGEMENT, BatchOperationIntent.SUSPENDED);
    final Record<BatchOperationLifecycleManagementRecordValue> resumedRecord =
        factory.generateRecordWithIntent(
            ValueType.BATCH_OPERATION_LIFECYCLE_MANAGEMENT, BatchOperationIntent.RESUMED);
    final Record<BatchOperationLifecycleManagementRecordValue> completedRecord =
        factory.generateRecordWithIntent(
            ValueType.BATCH_OPERATION_LIFECYCLE_MANAGEMENT, BatchOperationIntent.COMPLETED);

    // when
    final boolean handlesCanceled = handler.handlesRecord(canceledRecord);
    final boolean handlesSuspended = handler.handlesRecord(suspendedRecord);
    final boolean handlesResumed = handler.handlesRecord(resumedRecord);
    final boolean handlesCompleted = handler.handlesRecord(completedRecord);

    // then
    assertThat(handlesCanceled).isTrue();
    assertThat(handlesSuspended).isTrue();
    assertThat(handlesResumed).isTrue();
    assertThat(handlesCompleted).isTrue();
  }

  @Test
  void shouldNotHandleNonExportableIntent() {
    // given
    final Record<BatchOperationLifecycleManagementRecordValue> record =
        factory.generateRecordWithIntent(
            ValueType.BATCH_OPERATION_LIFECYCLE_MANAGEMENT, BatchOperationIntent.UNKNOWN);

    // when
    final boolean result = handler.handlesRecord(record);

    // then
    assertThat(result).isFalse();
  }

  @Test
  void shouldGenerateIds() {
    // given
    final Record<BatchOperationLifecycleManagementRecordValue> record =
        factory.generateRecordWithIntent(
            ValueType.BATCH_OPERATION_LIFECYCLE_MANAGEMENT, BatchOperationIntent.CANCELED);

    // when
    final List<String> idList = handler.generateIds(record);

    // then
    assertThat(idList).containsExactly(String.valueOf(record.getValue().getBatchOperationKey()));
  }

  @Test
  void shouldCreateNewEntity() {
    // given
    final String id = "12345";

    // when
    final BatchOperationEntity entity = handler.createNewEntity(id);

    // then
    assertThat(entity.getId()).isEqualTo(id);
  }

  @Test
  void shouldUpdateEntityForCanceledIntent() {
    // given
    final Record<BatchOperationLifecycleManagementRecordValue> record =
        factory.generateRecord(
            ValueType.BATCH_OPERATION_LIFECYCLE_MANAGEMENT,
            r -> r.withIntent(BatchOperationIntent.CANCELED));

    final var entity = new BatchOperationEntity();

    // when
    handler.updateEntity(record, entity);

    // then
    assertThat(entity.getState()).isEqualTo(BatchOperationState.CANCELED);
    assertThat(entity.getEndDate()).isEqualTo(DateUtil.toOffsetDateTime(record.getTimestamp()));
  }

  @Test
  void shouldUpdateEntityForSuspendedIntent() {
    // given
    final Record<BatchOperationLifecycleManagementRecordValue> record =
        factory.generateRecord(
            ValueType.BATCH_OPERATION_LIFECYCLE_MANAGEMENT,
            r -> r.withIntent(BatchOperationIntent.SUSPENDED));

    final var entity = new BatchOperationEntity();

    // when
    handler.updateEntity(record, entity);

    // then
    assertThat(entity.getState()).isEqualTo(BatchOperationState.SUSPENDED);
    assertThat(entity.getEndDate()).isNull();
  }

  @Test
  void shouldUpdateEntityForResumedIntent() {
    // given
    final Record<BatchOperationLifecycleManagementRecordValue> record =
        factory.generateRecord(
            ValueType.BATCH_OPERATION_LIFECYCLE_MANAGEMENT,
            r -> r.withIntent(BatchOperationIntent.RESUMED));

    final var entity = new BatchOperationEntity();

    // when
    handler.updateEntity(record, entity);

    // then
    assertThat(entity.getState()).isEqualTo(BatchOperationState.ACTIVE);
    assertThat(entity.getEndDate()).isNull();
  }

  @Test
  void shouldUpdateEntityForCompletedIntent() {
    // given
    final Record<BatchOperationLifecycleManagementRecordValue> record =
        factory.generateRecord(
            ValueType.BATCH_OPERATION_LIFECYCLE_MANAGEMENT,
            r ->
                r.withIntent(BatchOperationIntent.COMPLETED)
                    .withValue(
                        ImmutableBatchOperationLifecycleManagementRecordValue.builder()
                            .withErrors(List.of())
                            .build()));

    final var entity = new BatchOperationEntity();

    // when
    handler.updateEntity(record, entity);

    // then
    assertThat(entity.getState()).isEqualTo(BatchOperationState.COMPLETED);
    assertThat(entity.getEndDate()).isNull();
  }

  @Test
  void shouldUpdateEntityForCompletedIntentWithErrors() {
    // given
    final var errorRecord = new BatchOperationError();
    errorRecord.setMessage("error message");
    errorRecord.setType(BatchOperationErrorType.QUERY_FAILED);
    errorRecord.setPartitionId(1);

    final Record<BatchOperationLifecycleManagementRecordValue> record =
        factory.generateRecord(
            ValueType.BATCH_OPERATION_LIFECYCLE_MANAGEMENT,
            r ->
                r.withIntent(BatchOperationIntent.COMPLETED)
                    .withValue(
                        ImmutableBatchOperationLifecycleManagementRecordValue.builder()
                            .withErrors(List.of(errorRecord))
                            .build()));

    final var entity = new BatchOperationEntity();

    // when
    handler.updateEntity(record, entity);

    // then
    assertThat(entity.getState()).isEqualTo(BatchOperationState.PARTIALLY_COMPLETED);
    assertThat(entity.getEndDate()).isNull();
    record
        .getValue()
        .getErrors()
        .forEach(
            error -> {
              entity.getErrors().stream()
                  .filter(e -> e.getPartitionId() == error.getPartitionId())
                  .findFirst()
                  .ifPresentOrElse(
                      be -> {
                        assertThat(be.getType()).isEqualTo(error.getType().name());
                        assertThat(be.getMessage()).isEqualTo(error.getMessage());
                      },
                      () -> Assertions.fail("Error not found in entity: " + error));
            });
  }

  @Test
  void shouldUpdateEntityForFailedIntent() {
    // given
    final var errorRecord = new BatchOperationError();
    errorRecord.setMessage("error message");
    errorRecord.setType(BatchOperationErrorType.QUERY_FAILED);
    errorRecord.setPartitionId(1);

    final Record<BatchOperationLifecycleManagementRecordValue> record =
        factory.generateRecord(
            ValueType.BATCH_OPERATION_LIFECYCLE_MANAGEMENT,
            r ->
                r.withIntent(BatchOperationIntent.FAILED)
                    .withValue(
                        ImmutableBatchOperationLifecycleManagementRecordValue.builder()
                            .withErrors(List.of(errorRecord))
                            .build()));

    final var entity = new BatchOperationEntity();

    // when
    handler.updateEntity(record, entity);

    // then
    assertThat(entity.getState()).isEqualTo(BatchOperationState.FAILED);
    assertThat(entity.getEndDate()).isNull();
    record
        .getValue()
        .getErrors()
        .forEach(
            error -> {
              entity.getErrors().stream()
                  .filter(e -> e.getPartitionId() == error.getPartitionId())
                  .findFirst()
                  .ifPresentOrElse(
                      be -> {
                        assertThat(be.getType()).isEqualTo(error.getType().name());
                        assertThat(be.getMessage()).isEqualTo(error.getMessage());
                      },
                      () -> Assertions.fail("Error not found in entity: " + error));
            });
  }

  @Test
  void shouldFlushEntityWithConditionalScript() throws Exception {
    // given
    final BatchRequest batchRequest = mock(BatchRequest.class);
    final BatchOperationEntity entity =
        new BatchOperationEntity()
            .setId("12345")
            .setState(BatchOperationState.CANCELED)
            .setEndDate(DateUtil.toOffsetDateTime(Instant.now().toEpochMilli()));

    // when
    handler.flush(null, entity, batchRequest);

    // then
    final var paramsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(batchRequest)
        .upsertWithScript(
            eq(indexName),
            eq("12345"),
            eq(entity),
            eq(BatchOperationLifecycleManagementHandler.CONDITIONAL_STATE_UPDATE_SCRIPT),
            paramsCaptor.capture());
    final Map<String, Object> params = paramsCaptor.getValue();
    assertThat(params).containsEntry("state", "CANCELED");
    assertThat(params).containsEntry("endDate", entity.getEndDate());
    assertThat(params).doesNotContainKey("errors");
  }

  @Test
  void shouldFlushEntityWithErrorsInScript() throws Exception {
    // given
    final BatchRequest batchRequest = mock(BatchRequest.class);
    final var errorEntity =
        new io.camunda.webapps.schema.entities.operation.BatchOperationErrorEntity()
            .setPartitionId(1)
            .setType("QUERY_FAILED")
            .setMessage("error message");
    final BatchOperationEntity entity =
        new BatchOperationEntity()
            .setId("12345")
            .setState(BatchOperationState.FAILED)
            .setEndDate(null)
            .setErrors(List.of(errorEntity));

    // when
    handler.flush(null, entity, batchRequest);

    // then - errors should be included in script params when present
    final var paramsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(batchRequest)
        .upsertWithScript(
            eq(indexName),
            eq("12345"),
            eq(entity),
            eq(BatchOperationLifecycleManagementHandler.CONDITIONAL_STATE_UPDATE_SCRIPT),
            paramsCaptor.capture());
    final Map<String, Object> params = paramsCaptor.getValue();
    assertThat(params).containsEntry("state", "FAILED");
    assertThat(params).containsEntry("errors", List.of(errorEntity));
  }
}
