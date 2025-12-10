/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbException;
import io.camunda.zeebe.db.ZeebeDbTransaction;
import io.camunda.zeebe.logstreams.impl.log.LogStreamBatchReaderImpl;
import io.camunda.zeebe.logstreams.log.LogRecordAwaiter;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.log.LogStreamBatchReader.Batch;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.log.LoggedEvent;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.stream.api.CommandResponseWriter;
import io.camunda.zeebe.stream.api.ProcessingResult;
import io.camunda.zeebe.stream.api.ProcessingResultBuilder;
import io.camunda.zeebe.stream.api.RecordProcessor;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.impl.records.RecordValues;
import io.camunda.zeebe.stream.impl.records.TypedRecordImpl;
import io.camunda.zeebe.stream.impl.records.UnwrittenRecord;
import io.camunda.zeebe.stream.impl.state.StreamProcessorDbState;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.camunda.zeebe.util.exception.UnrecoverableException;
import io.camunda.zeebe.util.health.FailureListener;
import io.camunda.zeebe.util.health.HealthMonitorable;
import io.camunda.zeebe.util.health.HealthReport;
import java.time.Instant;
import java.util.List;
import org.agrona.concurrent.BackoffIdleStrategy;

public final class SyncStreamProcessor implements LogRecordAwaiter, HealthMonitorable {
  Thread currentThread;
  private final int partitionId;
  private final LogStream logStream;
  private final CommandResponseWriter responseWriter;
  private final ZeebeDb<ZbColumnFamilies> zeebeDb;
  private final List<RecordProcessor> processors;
  private final StreamProcessorDbState state;
  private final RecordMetadata recordMetadata;
  private final RecordValues recordValues;

  public SyncStreamProcessor(
      final int partitionId,
      final LogStream logStream,
      final CommandResponseWriter responseWriter,
      final ZeebeDb<ZbColumnFamilies> zeebeDb,
      final List<RecordProcessor> processors,
      final RecordValues recordValues) {
    this.partitionId = partitionId;
    this.logStream = logStream;
    this.responseWriter = responseWriter;
    this.zeebeDb = zeebeDb;
    this.processors = processors;
    state = new StreamProcessorDbState(zeebeDb, zeebeDb.createContext());
    recordMetadata = new RecordMetadata();
    this.recordValues = recordValues;
  }

  public void startReplay() {
    currentThread =
        Thread.startVirtualThread(
            () -> {
              final var recoveredPosition = recover();
              replay(recoveredPosition);
            });
  }

  public void startProcessing() {
    currentThread =
        Thread.startVirtualThread(
            () -> {
              final var recoveredPosition = recover();
              process(recoveredPosition);
            });
  }

  private long recover() {
    final var batchReader = new LogStreamBatchReaderImpl(logStream.newLogStreamReader());
    final var processedPositionState = state.getLastProcessedPositionState();

    try (final var transactionContext = SyncTransactionContext.of(zeebeDb)) {
      final var replayedPosition =
          processedPositionState.getLastSuccessfulProcessedRecordPosition();

      batchReader.seekToNextBatch(replayedPosition);
      while (batchReader.hasNext()) {
        final var batch = batchReader.next();
        if (batch.sourcePosition() > replayedPosition) {
          replayBatch(transactionContext, batch);
        }
      }
      return replayedPosition;
    }
  }

  private void replay(final long replayedPosition) {
    final var batchReader = new LogStreamBatchReaderImpl(logStream.newLogStreamReader());
    batchReader.seekToNextBatch(replayedPosition);

    try (final var transactionContext = SyncTransactionContext.of(zeebeDb)) {
      //noinspection InfiniteLoopStatement
      while (true) {
        while (!batchReader.hasNext()) {
          wait();
        }

        final var batch = batchReader.next();
        replayBatch(transactionContext, batch);
      }
    } catch (final InterruptedException ignored) {
    }
  }

  void process(final long replayedPosition) {
    final var entryReader = logStream.newLogStreamReader();
    entryReader.seek(replayedPosition);
    final var processedPositionState = state.getLastProcessedPositionState();

    try (final var transactionContext = SyncTransactionContext.of(zeebeDb)) {
      while (!entryReader.hasNext()) {
        wait();
      }
      final var initialEntry = entryReader.next();
      final var writer = logStream.newLogStreamWriter();
      // TODO: We need a custom processing result builder that is tailored for batch processing
      //   It needs to store multiple responses
      //   We could also make it easier to iterate through *new* processing results and mark records
      //   as processed.

      ProcessingResult result;
      try (final var tx = transactionContext.getCurrentTransaction()) {
        try {
          final var resultBuilder = new BufferedProcessingResultBuilder(writer::canWriteEvents);
          final var followUp = processEntry(resultBuilder, initialEntry);
          processFollowUp(resultBuilder, followUp);
          result = resultBuilder.build();
        } catch (final RuntimeException e) {
          rollbackTransaction(tx);
          result = handleProcessingError(writer, initialEntry, e);
        }

        processedPositionState.markAsProcessed(initialEntry.getPosition());
        writeProcessingResult(writer, initialEntry, result);

        commitTransaction(tx);

        writeProcessingResponse(result);
        executeSideEffects(result);
      }
    } catch (final InterruptedException ignored) {

    }
  }

  private void commitTransaction(final ZeebeDbTransaction transaction)
      throws UnrecoverableException {
    final var idle = new BackoffIdleStrategy();
    while (true) {
      try {
        transaction.commit();
        return;
      } catch (final ZeebeDbException e) {
        // Recoverable. Idle, then retry.
        idle.idle();
      } catch (final Exception e) {
        throw new UnrecoverableException(e);
      }
    }
  }

  private void rollbackTransaction(final ZeebeDbTransaction transaction)
      throws UnrecoverableException {
    final var idle = new BackoffIdleStrategy();
    while (true) {
      try {
        transaction.rollback();
        return;
      } catch (final ZeebeDbException e) {
        // Recoverable. Idle, then retry.
        idle.idle();
      } catch (final Exception e) {
        throw new UnrecoverableException(e);
      }
    }
  }

  private void replayBatch(final TransactionContext transactionContext, final Batch batch) {
    final var processedPositionState = state.getLastProcessedPositionState();

    try (final var tx = transactionContext.getCurrentTransaction()) {
      for (final var entry : batch) {
        replayEntry(entry);
        processedPositionState.markAsProcessed(batch.sourcePosition());
      }
      commitTransaction(tx);
    }
  }

  private void replayEntry(final LoggedEvent entry) {
    entry.readMetadata(recordMetadata);
    if (recordMetadata.getRecordType() != RecordType.EVENT) {
      return;
    }

    final var value = recordValues.readRecordValue(entry, recordMetadata.getValueType());
    final var record = new TypedRecordImpl(partitionId);
    record.wrap(entry, recordMetadata, value);

    final var processor =
        processors.stream()
            .filter(candidate -> candidate.accepts(recordMetadata.getValueType()))
            .findFirst()
            .orElseThrow(() -> NoSuchProcessorException.forRecord(record));

    processor.replay(record);
  }

  private ProcessingResult processEntry(
      final ProcessingResultBuilder resultBuilder, final LoggedEvent entry) {
    entry.readMetadata(recordMetadata);
    if (recordMetadata.getRecordType() != RecordType.COMMAND) {
      return resultBuilder.build();
    }

    final var value = recordValues.readRecordValue(entry, recordMetadata.getValueType());
    final var record = new TypedRecordImpl(partitionId);
    record.wrap(entry, recordMetadata, value);

    return processRecord(resultBuilder, record);
  }

  private void processFollowUp(
      final ProcessingResultBuilder resultBuilder, final ProcessingResult followUp) {
    for (final var entry : followUp.getRecordBatch().entries()) {
      final var record =
          new UnwrittenRecord(
              entry.key(), partitionId, entry.recordValue(), entry.recordMetadata());

      processRecord(resultBuilder, record);
    }
  }

  private ProcessingResult processRecord(
      final ProcessingResultBuilder resultBuilder, final TypedRecord<?> record) {
    final var processor =
        processors.stream()
            .filter(candidate -> candidate.accepts(record.getValueType()))
            .findFirst()
            .orElseThrow(() -> NoSuchProcessorException.forRecord(record));

    return processor.process(record, resultBuilder);
  }

  private ProcessingResult handleProcessingError(
      final LogStreamWriter writer, final LoggedEvent initialEntry, final RuntimeException e) {
    final var resultBuilder = new BufferedProcessingResultBuilder(writer::canWriteEvents);
    initialEntry.readMetadata(recordMetadata);

    final var value = recordValues.readRecordValue(initialEntry, recordMetadata.getValueType());
    final var record = new TypedRecordImpl(partitionId);
    record.wrap(initialEntry, recordMetadata, value);

    final var processor =
        processors.stream()
            .filter(candidate -> candidate.accepts(record.getValueType()))
            .findFirst()
            .orElseThrow(() -> NoSuchProcessorException.forRecord(record));

    return processor.onProcessingError(e, record, resultBuilder);
  }

  private void writeProcessingResponse(final ProcessingResult result) {
    if (result.getProcessingResponse().isEmpty()) {
      return;
    }
    final var response = result.getProcessingResponse().get();
    final var responseValue = response.responseValue();
    final var recordMetadata = responseValue.recordMetadata();
    responseWriter
        .intent(recordMetadata.getIntent())
        .key(responseValue.key())
        .recordType(recordMetadata.getRecordType())
        .rejectionReason(BufferUtil.wrapString(recordMetadata.getRejectionReason()))
        .rejectionType(recordMetadata.getRejectionType())
        .partitionId(partitionId)
        .valueType(recordMetadata.getValueType())
        .valueWriter(responseValue.recordValue())
        .tryWriteResponse(response.requestStreamId(), response.requestId());
  }

  private void executeSideEffects(final ProcessingResult result) {
    final var idle = new BackoffIdleStrategy(5, 5, 1_000_000, 10_000_000);
    while (!result.executePostCommitTasks()) {
      idle.idle();
    }
  }

  private void writeProcessingResult(
      final LogStreamWriter writer, final LoggedEvent initialEntry, final ProcessingResult result) {
    final var idle = new BackoffIdleStrategy(5, 5, 1_000_000, 10_000_000);
    while (true) {
      final var successfulWrite =
          writer
              .tryWrite(
                  WriteContext.processingResult(),
                  result.getRecordBatch().entries(),
                  initialEntry.getPosition())
              .isRight();
      if (successfulWrite) {
        return;
      } else {
        idle.idle();
      }
    }
  }

  @Override
  public void onRecordAvailable() {
    notify();
  }

  @Override
  public String componentName() {
    return "StreamProcessor-%d".formatted(partitionId);
  }

  @Override
  public HealthReport getHealthReport() {
    if (currentThread == null) {
      return HealthReport.unhealthy(this).withMessage("Not yet started", Instant.now());
    } else if (currentThread.isAlive()) {
      return HealthReport.healthy(this);
    } else {
      return HealthReport.dead(this).withMessage("Stream processing failed", Instant.now());
    }
  }

  @Override
  public void addFailureListener(final FailureListener failureListener) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void removeFailureListener(final FailureListener failureListener) {
    throw new UnsupportedOperationException();
  }

  private interface SyncTransactionContext extends TransactionContext, AutoCloseable {
    static SyncTransactionContext of(final ZeebeDb<ZbColumnFamilies> zeebeDb) {
      zeebeDb.createContext();
      return null;
    }

    @Override
    void close();
  }
}
