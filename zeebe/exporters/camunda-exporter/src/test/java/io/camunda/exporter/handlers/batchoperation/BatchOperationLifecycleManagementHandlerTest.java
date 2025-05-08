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
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationLifecycleManagementRecordValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import io.camunda.zeebe.util.DateUtil;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
    final Record<BatchOperationLifecycleManagementRecordValue> pausedRecord =
        factory.generateRecordWithIntent(
            ValueType.BATCH_OPERATION_LIFECYCLE_MANAGEMENT, BatchOperationIntent.PAUSED);
    final Record<BatchOperationLifecycleManagementRecordValue> resumedRecord =
        factory.generateRecordWithIntent(
            ValueType.BATCH_OPERATION_LIFECYCLE_MANAGEMENT, BatchOperationIntent.RESUMED);

    // when
    final boolean handlesCanceled = handler.handlesRecord(canceledRecord);
    final boolean handlesPaused = handler.handlesRecord(pausedRecord);
    final boolean handlesResumed = handler.handlesRecord(resumedRecord);

    // then
    assertThat(handlesCanceled).isTrue();
    assertThat(handlesPaused).isTrue();
    assertThat(handlesResumed).isTrue();
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
  void shouldUpdateEntityForPausedIntent() {
    // given
    final Record<BatchOperationLifecycleManagementRecordValue> record =
        factory.generateRecord(
            ValueType.BATCH_OPERATION_LIFECYCLE_MANAGEMENT,
            r -> r.withIntent(BatchOperationIntent.PAUSED));

    final var entity = new BatchOperationEntity();

    // when
    handler.updateEntity(record, entity);

    // then
    assertThat(entity.getState()).isEqualTo(BatchOperationState.PAUSED);
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
  void shouldFlushEntity() throws Exception {
    // given
    final BatchRequest batchRequest = mock(BatchRequest.class);
    final BatchOperationEntity entity =
        new BatchOperationEntity()
            .setId("12345")
            .setState(BatchOperationState.CANCELED)
            .setEndDate(DateUtil.toOffsetDateTime(Instant.now().toEpochMilli()));

    // when
    handler.flush(entity, batchRequest);

    // then
    final var argumentCaptor = ArgumentCaptor.forClass(Map.class);
    verify(batchRequest).update(eq(indexName), eq("12345"), argumentCaptor.capture());
    final Map<String, Object> updateFields = argumentCaptor.getValue();
    assertThat(updateFields).containsEntry("state", BatchOperationState.CANCELED);
    assertThat(updateFields).containsEntry("endDate", entity.getEndDate());
  }
}
