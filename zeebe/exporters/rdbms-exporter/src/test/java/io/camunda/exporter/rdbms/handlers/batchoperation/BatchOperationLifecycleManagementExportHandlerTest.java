/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers.batchoperation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.db.rdbms.write.service.BatchOperationWriter;
import io.camunda.db.rdbms.write.service.HistoryCleanupService;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationState;
import io.camunda.search.entities.BatchOperationType;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCache;
import io.camunda.zeebe.exporter.common.cache.batchoperation.CachedBatchOperationEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationErrorType;
import io.camunda.zeebe.protocol.record.value.BatchOperationLifecycleManagementRecordValue;
import io.camunda.zeebe.protocol.record.value.ImmutableBatchOperationLifecycleManagementRecordValue;
import io.camunda.zeebe.protocol.record.value.scaling.ImmutableBatchOperationErrorValue;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class BatchOperationLifecycleManagementExportHandlerTest {

  private final ProtocolFactory factory = new ProtocolFactory();

  @SuppressWarnings("unchecked")
  private final ExporterEntityCache<String, CachedBatchOperationEntity> batchOperationCache =
      mock(ExporterEntityCache.class);

  private final BatchOperationWriter batchOperationWriter = mock(BatchOperationWriter.class);
  private final HistoryCleanupService historyCleanupService = mock(HistoryCleanupService.class);

  private final BatchOperationLifecycleManagementExportHandler handler =
      new BatchOperationLifecycleManagementExportHandler(
          batchOperationWriter, historyCleanupService, batchOperationCache);

  @ParameterizedTest
  @EnumSource(
      value = BatchOperationIntent.class,
      names = {"COMPLETED", "FAILED"})
  void shouldInsertErrorsOnceWhenCacheLookupFailsAndRecordIsRetried(
      final BatchOperationIntent intent) {
    // given
    final var record = createRecordWithError(intent);
    final var batchOperationKey = String.valueOf(record.getValue().getBatchOperationKey());
    when(batchOperationCache.get(batchOperationKey))
        .thenThrow(new RuntimeException("transient DB error"))
        .thenReturn(
            Optional.of(
                new CachedBatchOperationEntity(
                    batchOperationKey, BatchOperationType.CANCEL_PROCESS_INSTANCE)));

    // when
    assertThatThrownBy(() -> handler.export(record)).hasMessage("transient DB error");
    handler.export(record);

    // then
    final var expectedState =
        intent == BatchOperationIntent.FAILED
            ? BatchOperationState.FAILED
            : BatchOperationState.PARTIALLY_COMPLETED;
    verify(batchOperationWriter, times(1))
        .finishWithErrors(eq(batchOperationKey), any(), any(), eq(expectedState));
    verify(historyCleanupService, times(1))
        .scheduleBatchOperationForHistoryCleanup(
            eq(batchOperationKey), eq(BatchOperationType.CANCEL_PROCESS_INSTANCE), any());
  }

  private Record<BatchOperationLifecycleManagementRecordValue> createRecordWithError(
      final BatchOperationIntent intent) {
    return factory.generateRecord(
        ValueType.BATCH_OPERATION_LIFECYCLE_MANAGEMENT,
        b ->
            b.withIntent(intent)
                .withValue(
                    ImmutableBatchOperationLifecycleManagementRecordValue.builder()
                        .from(
                            factory.generateObject(
                                BatchOperationLifecycleManagementRecordValue.class))
                        .withErrors(
                            List.of(
                                ImmutableBatchOperationErrorValue.builder()
                                    .withPartitionId(1)
                                    .withType(BatchOperationErrorType.QUERY_FAILED)
                                    .withMessage("failed")
                                    .build()))
                        .build()));
  }
}
