/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions;

import static java.util.Objects.requireNonNull;

import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.partitioning.PartitionAdminAccess;
import io.camunda.zeebe.engine.api.records.RecordBatchEntry;
import io.camunda.zeebe.engine.state.processing.DbBannedInstanceState;
import io.camunda.zeebe.logstreams.log.LogStreamRecordWriter;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.error.ErrorRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ErrorIntent;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.io.IOException;
import java.util.Optional;
import org.slf4j.Logger;

class ZeebePartitionAdminAccess implements PartitionAdminAccess {

  private static final Logger LOG = Loggers.SYSTEM_LOGGER;

  private final ConcurrencyControl concurrencyControl;
  private final int partitionId;
  private final PartitionAdminControl adminControl;

  ZeebePartitionAdminAccess(
      final ConcurrencyControl concurrencyControl,
      final int partitionId,
      final PartitionAdminControl adminControl) {
    this.concurrencyControl = requireNonNull(concurrencyControl);
    this.partitionId = partitionId;
    this.adminControl = requireNonNull(adminControl);
  }

  @Override
  public Optional<PartitionAdminAccess> forPartition(final int partitionId) {
    if (this.partitionId == partitionId) {
      return Optional.of(this);
    } else {
      return Optional.empty();
    }
  }

  @Override
  public ActorFuture<Void> takeSnapshot() {
    final ActorFuture<Void> completed = concurrencyControl.createFuture();

    concurrencyControl.run(
        () -> {
          try {
            adminControl.triggerSnapshot();
            completed.complete(null);
          } catch (final Exception e) {
            completed.completeExceptionally(e);
          }
        });

    return completed;
  }

  @Override
  public ActorFuture<Void> pauseExporting() {
    final ActorFuture<Void> completed = concurrencyControl.createFuture();
    concurrencyControl.run(
        () -> {
          try {
            final var pauseStatePersisted = adminControl.pauseExporting();

            if (adminControl.getExporterDirector() != null && pauseStatePersisted) {
              adminControl.getExporterDirector().pauseExporting().onComplete(completed);
            } else {
              completed.complete(null);
            }
          } catch (final IOException e) {
            LOG.error("Could not pause exporting", e);
            completed.completeExceptionally(e);
          }
        });
    return completed;
  }

  @Override
  public ActorFuture<Void> resumeExporting() {
    final ActorFuture<Void> completed = concurrencyControl.createFuture();
    concurrencyControl.run(
        () -> {
          try {
            adminControl.resumeExporting();
            if (adminControl.getExporterDirector() != null && adminControl.shouldExport()) {
              adminControl.getExporterDirector().resumeExporting().onComplete(completed);
            } else {
              completed.complete(null);
            }
          } catch (final IOException e) {
            LOG.error("Could not resume exporting", e);
            completed.completeExceptionally(e);
          }
        });
    return completed;
  }

  @Override
  public ActorFuture<Void> pauseProcessing() {
    final ActorFuture<Void> completed = concurrencyControl.createFuture();
    concurrencyControl.run(
        () -> {
          try {
            adminControl.pauseProcessing();

            if (adminControl.getStreamProcessor() != null && !adminControl.shouldProcess()) {
              adminControl.getStreamProcessor().pauseProcessing().onComplete(completed);
            } else {
              completed.complete(null);
            }
          } catch (final IOException e) {
            LOG.error("Could not pause processing state", e);
            completed.completeExceptionally(e);
          }
        });
    return completed;
  }

  @Override
  public ActorFuture<Void> resumeProcessing() {
    final ActorFuture<Void> completed = concurrencyControl.createFuture();
    concurrencyControl.run(
        () -> {
          try {
            adminControl.resumeProcessing();
            if (adminControl.getStreamProcessor() != null && adminControl.shouldProcess()) {
              adminControl.getStreamProcessor().resumeProcessing();
            }
            completed.complete(null);
          } catch (final IOException e) {
            LOG.error("Could not resume processing", e);
            completed.completeExceptionally(e);
          }
        });
    return completed;
  }

  @Override
  public ActorFuture<Void> banInstance(final long processInstanceKey) {
    final ActorFuture<Void> future = concurrencyControl.createFuture();
    concurrencyControl.run(
        () -> {
          try {
            adminControl
                .getLogStream()
                .newLogStreamRecordWriter()
                .onComplete(
                    (writer, error) -> {
                      if (error != null) {
                        LOG.error(
                            "Could not retrieve writer to write error record for process instance.",
                            error);
                        future.completeExceptionally(error);
                        return;
                      }

                      writeErrorEventAndBanInstance(processInstanceKey, writer, future);
                    });
          } catch (final Exception e) {
            LOG.error("Could not resume processing", e);
            future.completeExceptionally(e);
          }
        });
    return future;
  }

  private void writeErrorEventAndBanInstance(
      final long processInstanceKey,
      final LogStreamRecordWriter writer,
      final ActorFuture<Void> future) {

    final long position = tryWriteErrorEvent(writer, processInstanceKey);

    if (position >= 0) {
      // successful write operation
      LOG.info("Wrote error record on position {}", position);
      // we only want to make the state change after we wrote the event
      banInstanceInState(processInstanceKey);
      LOG.info("Successfully banned instance with key {}", processInstanceKey);
      future.complete(null);
    } else {
      final String errorMsg =
          String.format("Failure on writing error record to ban instance %d", processInstanceKey);
      future.completeExceptionally(new IllegalStateException(errorMsg));
      LOG.error(errorMsg);
    }
  }

  private void banInstanceInState(final long processInstanceKey) {
    final var zeebeDb = adminControl.getZeebeDb();
    final var context = zeebeDb.createContext();
    final var dbBannedInstanceState = new DbBannedInstanceState(zeebeDb, context, partitionId);

    dbBannedInstanceState.banProcessInstance(processInstanceKey);
  }

  private static long tryWriteErrorEvent(
      final LogStreamRecordWriter writer, final long processInstanceKey) {
    final var errorRecord = new ErrorRecord();
    errorRecord.initErrorRecord(new Exception("Instance was banned from outside."), -1);
    errorRecord.setProcessInstanceKey(processInstanceKey);

    final var recordMetadata =
        new RecordMetadata()
            .recordType(RecordType.EVENT)
            .valueType(ValueType.ERROR)
            .intent(ErrorIntent.CREATED)
            .rejectionType(RejectionType.NULL_VAL)
            .rejectionReason("");

    final var entry =
        RecordBatchEntry.createEntry(
            processInstanceKey,
            -1,
            recordMetadata.getRecordType(),
            recordMetadata.getIntent(),
            recordMetadata.getRejectionType(),
            recordMetadata.getRejectionReason(),
            recordMetadata.getValueType(),
            errorRecord);

    return writer.metadataWriter(recordMetadata).valueWriter(entry.recordValue()).tryWrite();
  }
}
