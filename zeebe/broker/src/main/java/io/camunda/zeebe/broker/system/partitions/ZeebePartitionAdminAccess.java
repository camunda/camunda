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
import io.camunda.zeebe.engine.state.processing.DbBannedInstanceState;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.log.LogStreamWriter.WriteFailure;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.error.ErrorRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ErrorIntent;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.stream.impl.records.RecordBatchEntry;
import io.camunda.zeebe.util.Either;
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
  public ActorFuture<Void> softPauseExporting() {
    final ActorFuture<Void> completed = concurrencyControl.createFuture();
    concurrencyControl.run(
        () -> {
          try {
            final var softPauseStatePersisted = adminControl.softPauseExporting();

            if (adminControl.getExporterDirector() != null && softPauseStatePersisted) {
              adminControl.getExporterDirector().softPauseExporting().onComplete(completed);
            } else {
              completed.complete(null);
            }
          } catch (final IOException e) {
            LOG.error("Could not soft pause exporting", e);
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
                .newLogStreamWriter()
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
            LOG.error(
                "Failure on writing error record to ban instance {} onto the LogStream.",
                processInstanceKey,
                e);
            future.completeExceptionally(e);
          }
        });
    return future;
  }

  private void writeErrorEventAndBanInstance(
      final long processInstanceKey, final LogStreamWriter writer, final ActorFuture<Void> future) {
    tryWriteErrorEvent(writer, processInstanceKey)
        .ifRightOrLeft(
            (position) -> {
              LOG.info("Wrote error record on position {}", position);
              // we only want to make the state change after we wrote the event
              banInstanceInState(processInstanceKey);
              LOG.info("Successfully banned instance with key {}", processInstanceKey);
              future.complete(null);
            },
            writeFailure -> {
              final String errorMsg =
                  String.format(
                      "Failure %s on writing error record to ban instance %d",
                      writeFailure, processInstanceKey);
              future.completeExceptionally(new IllegalStateException(errorMsg));
              LOG.error(errorMsg);
            });
  }

  private void banInstanceInState(final long processInstanceKey) {
    final var zeebeDb = adminControl.getZeebeDb();
    final var context = zeebeDb.createContext();
    final var dbBannedInstanceState = new DbBannedInstanceState(zeebeDb, context);

    dbBannedInstanceState.banProcessInstance(processInstanceKey);
  }

  private static Either<WriteFailure, Long> tryWriteErrorEvent(
      final LogStreamWriter writer, final long processInstanceKey) {
    final var errorRecord = new ErrorRecord();
    errorRecord.initErrorRecord(new Exception("Instance was banned from outside."), -1);
    errorRecord.setProcessInstanceKey(processInstanceKey);

    final var recordMetadata =
        new RecordMetadata()
            .recordType(RecordType.EVENT)
            .valueType(ValueType.ERROR)
            .intent(ErrorIntent.CREATED)
            .recordVersion(RecordMetadata.DEFAULT_RECORD_VERSION)
            .rejectionType(RejectionType.NULL_VAL)
            .rejectionReason("");
    final var entry =
        RecordBatchEntry.createEntry(processInstanceKey, recordMetadata, -1, errorRecord);
    return writer.tryWrite(entry);
  }
}
