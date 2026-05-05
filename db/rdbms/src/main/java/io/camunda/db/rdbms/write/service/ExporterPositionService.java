/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.service;

import io.camunda.db.rdbms.sql.ExporterPositionMapper;
import io.camunda.db.rdbms.write.domain.ExporterPositionModel;
import io.camunda.db.rdbms.write.queue.ContextType;
import io.camunda.db.rdbms.write.queue.ExecutionQueue;
import io.camunda.db.rdbms.write.queue.QueueItem;
import io.camunda.db.rdbms.write.queue.WriteStatementType;
import java.util.function.LongSupplier;

public class ExporterPositionService {

  private final ExecutionQueue executionQueue;
  private final ExporterPositionMapper exporterPositionMapper;

  public ExporterPositionService(
      final ExecutionQueue executionQueue, final ExporterPositionMapper exporterPositionMapper) {
    this.executionQueue = executionQueue;
    this.exporterPositionMapper = exporterPositionMapper;
  }

  public void create(final ExporterPositionModel variable) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.EXPORTER_POSITION,
            WriteStatementType.INSERT,
            variable.partitionId(),
            "io.camunda.db.rdbms.sql.ExporterPositionMapper.insert",
            variable));
  }

  public void createWithoutQueue(final ExporterPositionModel positionModel) {
    exporterPositionMapper.insert(positionModel);
  }

  public void update(final ExporterPositionModel variable) {
    executionQueue.executeInQueue(
        new QueueItem(
            ContextType.EXPORTER_POSITION,
            WriteStatementType.UPDATE,
            variable.partitionId(),
            "io.camunda.db.rdbms.sql.ExporterPositionMapper.update",
            variable));
  }

  public ExporterPositionModel findOne(final int key) {
    return exporterPositionMapper.findOne(key);
  }

  /**
   * Registers a hook that, at the start of every flush transaction, acquires a row-level lock (
   * {@code SELECT FOR UPDATE}) on the exporter position row for the given partition and validates
   * that the position stored in the database matches the expected value provided by {@code
   * expectedPositionSupplier}.
   *
   * <p>This prevents two exporter instances for the same partition from writing to the database
   * concurrently: only one instance can hold the row lock at a time, and any instance whose local
   * position has diverged from the database will fail fast instead of silently overwriting data.
   *
   * @param partitionId the partition whose position row should be locked
   * @param expectedPositionSupplier supplies the position value that the database is expected to
   *     hold at the time the transaction begins; evaluated on every flush
   */
  public void registerLockPositionHook(
      final int partitionId, final LongSupplier expectedPositionSupplier) {
    executionQueue.registerInTransactionHook(
        session -> {
          final ExporterPositionMapper mapper = session.getMapper(ExporterPositionMapper.class);
          final ExporterPositionModel lockedPosition = mapper.findOneForUpdate(partitionId);
          if (lockedPosition != null) {
            final long expectedPosition = expectedPositionSupplier.getAsLong();
            if (lockedPosition.lastExportedPosition() != expectedPosition) {
              throw new ExporterPositionMismatchException(
                  String.format(
                      "Exporter position mismatch for partition %d: expected %d but found %d. "
                          + "Another exporter instance may have already exported to this partition.",
                      partitionId, expectedPosition, lockedPosition.lastExportedPosition()));
            }
          }
        });
  }
}
