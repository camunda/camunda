/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms.handlers.batchoperation;

import static io.camunda.zeebe.protocol.record.intent.BatchOperationIntent.*;

import io.camunda.db.rdbms.sql.BatchOperationMapper.BatchOperationErrorDto;
import io.camunda.db.rdbms.sql.BatchOperationMapper.BatchOperationErrorsDto;
import io.camunda.db.rdbms.write.service.BatchOperationWriter;
import io.camunda.db.rdbms.write.service.HistoryCleanupService;
import io.camunda.exporter.rdbms.RdbmsExportHandler;
import io.camunda.search.entities.BatchOperationEntity.BatchOperationState;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCache;
import io.camunda.zeebe.exporter.common.cache.batchoperation.CachedBatchOperationEntity;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.value.BatchOperationLifecycleManagementRecordValue;
import io.camunda.zeebe.protocol.record.value.scaling.BatchOperationErrorValue;
import io.camunda.zeebe.util.DateUtil;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchOperationLifecycleManagementExportHandler
    implements RdbmsExportHandler<BatchOperationLifecycleManagementRecordValue> {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(BatchOperationLifecycleManagementExportHandler.class);
  private static final Set<Intent> EXPORTABLE_INTENTS =
      Set.of(CANCELED, SUSPENDED, RESUMED, COMPLETED, FAILED);

  private final BatchOperationWriter batchOperationWriter;

  private final HistoryCleanupService historyCleanupService;

  private final ExporterEntityCache<String, CachedBatchOperationEntity> batchOperationCache;

  public BatchOperationLifecycleManagementExportHandler(
      final BatchOperationWriter batchOperationWriter,
      final HistoryCleanupService historyCleanupService,
      final ExporterEntityCache<String, CachedBatchOperationEntity> batchOperationCache) {
    this.batchOperationWriter = batchOperationWriter;
    this.historyCleanupService = historyCleanupService;
    this.batchOperationCache = batchOperationCache;
  }

  @Override
  public boolean canExport(final Record<BatchOperationLifecycleManagementRecordValue> record) {
    return record.getValueType() == ValueType.BATCH_OPERATION_LIFECYCLE_MANAGEMENT
        && EXPORTABLE_INTENTS.contains(record.getIntent());
  }

  @Override
  public void export(final Record<BatchOperationLifecycleManagementRecordValue> record) {
    final var value = record.getValue();
    final var batchOperationKey = String.valueOf(value.getBatchOperationKey());
    final var endDate = DateUtil.toOffsetDateTime(record.getTimestamp());
    switch (record.getIntent()) {
      case CANCELED -> {
        final var cachedEntity = batchOperationCache.get(batchOperationKey);
        batchOperationWriter.cancel(batchOperationKey, endDate);
        scheduleBatchOperationForHistoryCleanup(cachedEntity, batchOperationKey, endDate);
      }
      case SUSPENDED -> batchOperationWriter.suspend(batchOperationKey);
      case RESUMED -> batchOperationWriter.resume(batchOperationKey);
      case COMPLETED -> {
        final var cachedEntity = batchOperationCache.get(batchOperationKey);
        if (value.getErrors().isEmpty()) {
          batchOperationWriter.finish(batchOperationKey, endDate);
        } else {
          batchOperationWriter.finishWithErrors(
              batchOperationKey,
              endDate,
              mapErrors(batchOperationKey, value.getErrors()),
              BatchOperationState.PARTIALLY_COMPLETED);
        }
        scheduleBatchOperationForHistoryCleanup(cachedEntity, batchOperationKey, endDate);
      }
      case FAILED -> {
        final var cachedEntity = batchOperationCache.get(batchOperationKey);
        batchOperationWriter.finishWithErrors(
            batchOperationKey,
            endDate,
            mapErrors(batchOperationKey, value.getErrors()),
            BatchOperationState.FAILED);

        scheduleBatchOperationForHistoryCleanup(cachedEntity, batchOperationKey, endDate);
      }
      default -> // should never happen
          LOGGER.warn(
              "Trying to export an un-supported batch operation lifecycle management intent: {}",
              record.getIntent());
    }
  }

  /**
   * Takes the cache entry instead of looking it up, because the lookup can read from the database
   * and fail. Callers look it up before queueing any write: the exporter retries a failed record,
   * so writes queued before the failure would be queued twice, and batch operation errors have no
   * unique key that would reject the duplicates.
   */
  private void scheduleBatchOperationForHistoryCleanup(
      final Optional<CachedBatchOperationEntity> cachedEntity,
      final String batchOperationKey,
      final OffsetDateTime endDate) {
    cachedEntity.ifPresent(
        entity ->
            historyCleanupService.scheduleBatchOperationForHistoryCleanup(
                batchOperationKey, entity.type(), endDate));
  }

  private BatchOperationErrorsDto mapErrors(
      final String batchOperationKey, final List<BatchOperationErrorValue> errors) {
    final var errorsDto =
        errors.stream()
            .map(
                e ->
                    new BatchOperationErrorDto(
                        e.getPartitionId(), e.getType().name(), e.getMessage()))
            .toList();

    return new BatchOperationErrorsDto(batchOperationKey, errorsDto);
  }
}
