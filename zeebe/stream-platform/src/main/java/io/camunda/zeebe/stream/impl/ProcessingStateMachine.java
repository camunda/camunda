/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDbTransaction;
import io.camunda.zeebe.logstreams.impl.Loggers;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.LogStreamReader;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.log.LoggedEvent;
import io.camunda.zeebe.logstreams.log.WriteContext;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.error.ErrorRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ErrorIntent;
import io.camunda.zeebe.scheduler.ActorControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.retry.AbortableRetryStrategy;
import io.camunda.zeebe.scheduler.retry.RecoverableRetryStrategy;
import io.camunda.zeebe.scheduler.retry.RetryStrategy;
import io.camunda.zeebe.stream.api.EmptyProcessingResult;
import io.camunda.zeebe.stream.api.EventFilter;
import io.camunda.zeebe.stream.api.ProcessingResponse;
import io.camunda.zeebe.stream.api.ProcessingResult;
import io.camunda.zeebe.stream.api.ProcessingResultBuilder;
import io.camunda.zeebe.stream.api.RecordProcessor;
import io.camunda.zeebe.stream.api.StreamClock.ControllableStreamClock;
import io.camunda.zeebe.stream.api.records.ExceededBatchRecordSizeException;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.scheduling.ScheduledCommandCache;
import io.camunda.zeebe.stream.api.state.MutableLastProcessedPositionState;
import io.camunda.zeebe.stream.impl.metrics.ProcessingMetrics;
import io.camunda.zeebe.stream.impl.records.RecordValues;
import io.camunda.zeebe.stream.impl.records.TypedRecordImpl;
import io.camunda.zeebe.stream.impl.records.UnwrittenRecord;
import io.camunda.zeebe.util.CloseableSilently;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.camunda.zeebe.util.exception.RecoverableException;
import io.camunda.zeebe.util.exception.UnrecoverableException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;

/**
 * Represents the processing state machine, which is executed on normal processing.
 *
 * <pre>
 *
 * +------------------+            +--------------------+
 * |                  |            |                    |      exception
 * | tryToReadNextRecord() |----------->|  processCommand()  |------------------+
 * |                  |            |                    |                  v
 * +------------------+            +--------------------+            +---------------+
 *           ^                             |                         |               |------+
 *           |                             |         +-------------->|   onError()   |      | exception
 *           |                             |         |  exception    |               |<-----+
 *           |                     +-------v-------------+           +---------------+
 *           |                     |                     |                 |
 *           |                     |   writeRecords()    |                 |
 *           |                     |                     |<----------------+
 * +----------------------+        +---------------------+
 * |                      |                 |
 * | executeSideEffects() |                 v
 * |                      |       +----------------------+
 * +----------------------+       |                      |
 *           ^                    |     updateState()    |
 *           +--------------------|                      |
 *                                +----------------------+
 *                                       ^      |
 *                                       |      | exception
 *                                       |      |
 *                                    +---------v----+
 *                                    |              |
 *                                    |   onError()  |
 *                                    |              |
 *                                    +--------------+
 *                                       ^     |
 *                                       |     |  exception
 *                                       +-----+
 *
 * </pre>
 */
public final class ProcessingStateMachine {

  public static final String WARN_MESSAGE_BATCH_PROCESSING_RETRY =
      "Expected to process commands in a batch, but exceeded the resulting batch size after processing {} commands (maxCommandsInBatch: {}).";
  private static final Logger LOG = Loggers.PROCESSOR_LOGGER;
  private static final String ERROR_MESSAGE_WRITE_RECORD_ABORTED =
      "Expected to write one or more follow-up records for record '{} {}' without errors, but exception was thrown.";
  private static final String ERROR_MESSAGE_ROLLBACK_ABORTED =
      "Expected to roll back the current transaction for record '{} {}' successfully, but exception was thrown.";
  private static final String ERROR_MESSAGE_EXECUTE_SIDE_EFFECT_ABORTED =
      "Expected to execute side effects for record '{} {}' successfully, but exception was thrown.";
  private static final String ERROR_MESSAGE_UPDATE_STATE_FAILED =
      "Expected to successfully update state for record '{} {}', but caught an exception. Retry.";
  private static final String ERROR_MESSAGE_PROCESSING_FAILED_RETRY_PROCESSING =
      "Expected to process record '{} {}' successfully on stream processor, but caught recoverable exception. Retry processing.";
  private static final String ERROR_MESSAGE_PROCESSING_FAILED_UNRECOVERABLE =
      "Expected to process record '{} {}' successfully on stream processor, but caught unrecoverable exception.";
  private static final String NOTIFY_PROCESSED_LISTENER_ERROR_MESSAGE =
      "Expected to invoke processed listener for record {} successfully, but exception was thrown.";
  private static final String NOTIFY_SKIPPED_LISTENER_ERROR_MESSAGE =
      "Expected to invoke skipped listener for record '{} {}' successfully, but exception was thrown.";
  private static final Duration PROCESSING_RETRY_DELAY = Duration.ofMillis(250);
  private static final String ERROR_MESSAGE_HANDLING_PROCESSING_ERROR_FAILED =
      "Expected to process command '{} {}' successfully on stream processor, but caught unexpected exception. Failed to handle the exception gracefully.";
  private final EventFilter processingFilter;
  private final EventFilter isEventOrRejection =
      new MetadataEventFilter(
          recordMetadata -> {
            final var recordType = recordMetadata.getRecordType();
            return recordType == RecordType.EVENT || recordType == RecordType.COMMAND_REJECTION;
          });
  private final MutableLastProcessedPositionState lastProcessedPositionState;
  private final RecordMetadata metadata = new RecordMetadata();
  private final ActorControl actor;
  private final LogStreamReader logStreamReader;
  private final TransactionContext transactionContext;
  private final RetryStrategy writeRetryStrategy;
  private final RetryStrategy sideEffectsRetryStrategy;
  private final RetryStrategy updateStateRetryStrategy;
  private final BooleanSupplier shouldProcessNext;
  private final BooleanSupplier abortCondition;
  private final RecordValues recordValues;
  private final TypedRecordImpl typedCommand;
  private final StreamProcessorListener streamProcessorListener;
  // current iteration
  private LoggedEvent currentRecord;
  private ZeebeDbTransaction zeebeDbTransaction;
  private long writtenPosition = StreamProcessor.UNSET_POSITION;
  private long lastSuccessfulProcessedRecordPosition = StreamProcessor.UNSET_POSITION;
  private long lastWrittenPosition = StreamProcessor.UNSET_POSITION;
  private int onErrorRetries;
  // Used for processing duration metrics
  private CloseableSilently processingTimer;
  private boolean reachedEnd = true;
  private final StreamProcessorContext context;
  private final List<RecordProcessor> recordProcessors;
  private ProcessingResult currentProcessingResult;
  private List<LogAppendEntry> pendingWrites;
  private Collection<ProcessingResponse> pendingResponses;
  private RecordProcessor currentProcessor;
  private final LogStreamWriter logStreamWriter;
  private boolean inProcessing;
  private final int maxCommandsInBatch;
  private int processedCommandsCount;
  private final ProcessingMetrics processingMetrics;
  private final ScheduledCommandCache scheduledCommandCache;
  private volatile ErrorHandlingPhase errorHandlingPhase = ErrorHandlingPhase.NO_ERROR;
  private final ControllableStreamClock clock;

  public ProcessingStateMachine(
      final StreamProcessorContext context,
      final BooleanSupplier shouldProcessNext,
      final List<RecordProcessor> recordProcessors,
      final ScheduledCommandCache scheduledCommandCache) {
    this.context = context;
    this.recordProcessors = recordProcessors;
    this.scheduledCommandCache = scheduledCommandCache;
    actor = context.getActor();
    recordValues = context.getRecordValues();
    logStreamReader = context.getLogStreamReader();
    logStreamWriter = context.getLogStreamWriter();
    transactionContext = context.getTransactionContext();
    abortCondition = context.getAbortCondition();
    lastProcessedPositionState = context.getLastProcessedPositionState();
    maxCommandsInBatch = context.getMaxCommandsInBatch();

    writeRetryStrategy = new AbortableRetryStrategy(actor);
    sideEffectsRetryStrategy = new AbortableRetryStrategy(actor);
    updateStateRetryStrategy = new RecoverableRetryStrategy(actor);
    this.shouldProcessNext = shouldProcessNext;

    final int partitionId = context.getLogStream().getPartitionId();
    typedCommand = new TypedRecordImpl(partitionId);

    streamProcessorListener = context.getStreamProcessorListener();
    processingMetrics = new ProcessingMetrics(context.getMeterRegistry());
    processingFilter =
        new MetadataEventFilter(
                recordMetadata -> recordMetadata.getRecordType() == RecordType.COMMAND)
            .and(record -> !record.shouldSkipProcessing())
            .and(context.processingFilter());
    clock = context.getClock();
  }

  private void skipRecord() {
    notifySkippedListener(currentRecord);
    markProcessingCompleted();
    actor.submit(this::tryToReadNextRecord);
    processingMetrics.eventSkipped();
  }

  void markProcessingCompleted() {
    inProcessing = false;
    if (onErrorRetries > 0) {
      onErrorRetries = 0;
      updateErrorHandlingPhase(ErrorHandlingPhase.NO_ERROR);
    }
  }

  void tryToReadNextRecord() {
    final var hasNext = logStreamReader.hasNext();

    if (currentRecord != null) {
      final var previousRecord = currentRecord;
      // All commands cause a follow-up event or rejection, which means the processor
      // reached the end of the log if:
      //  * the last record was an event or rejection
      //  * and there is no next record on the log
      //  * and this was the last record written (records that have been written to the dispatcher
      //    might not be written to the log yet, which means they will appear shortly after this)
      reachedEnd =
          isEventOrRejection.applies(previousRecord)
              && !hasNext
              && lastWrittenPosition <= previousRecord.getPosition();
    }

    if (shouldProcessNext.getAsBoolean() && hasNext && !inProcessing) {
      currentRecord = logStreamReader.next();

      if (processingFilter.applies(currentRecord)) {
        processCommand(currentRecord);
      } else {
        skipRecord();
      }
    }
  }

  /**
   * Be aware this is a transient property which can change anytime, e.g. if a new command is
   * written to the log.
   *
   * @return true if the ProcessingStateMachine has reached the end of the log and nothing is left
   *     to being processed/applied, false otherwise
   */
  public boolean hasReachedEnd() {
    return reachedEnd;
  }

  private void processCommand(final LoggedEvent loggedEvent) {
    // we have to mark ourself has inProcessing to not interfere with readNext calls, which
    // are triggered from commit listener
    inProcessing = true;

    currentProcessingResult = EmptyProcessingResult.INSTANCE;

    metadata.reset();
    loggedEvent.readMetadata(metadata);

    try {
      // Here we need to get the current time, since we want to calculate
      // how long it took between writing to the dispatcher and processing.
      // In all other cases we should prefer to use the Prometheus Timer API.
      processingMetrics.processingLatency(loggedEvent.getTimestamp(), clock.millis());
      processingTimer =
          processingMetrics.startProcessingDurationTimer(
              metadata.getValueType(), metadata.getIntent());

      final var value = recordValues.readRecordValue(loggedEvent, metadata.getValueType());
      typedCommand.wrap(loggedEvent, metadata, value);

      LOG.trace("Processing command: {}", metadata);

      zeebeDbTransaction = transactionContext.getCurrentTransaction();
      try (final var timer = processingMetrics.startBatchProcessingDurationTimer()) {
        zeebeDbTransaction.run(() -> batchProcessing(typedCommand));
        processingMetrics.observeCommandCount(processedCommandsCount);
      }

      finalizeCommandProcessing();
      writeRecords();
    } catch (final RecoverableException recoverableException) {
      // recoverable
      LOG.error(
          ERROR_MESSAGE_PROCESSING_FAILED_RETRY_PROCESSING,
          loggedEvent,
          metadata,
          recoverableException);
      actor.schedule(PROCESSING_RETRY_DELAY, () -> processCommand(currentRecord));
    } catch (final UnrecoverableException unrecoverableException) {
      LOG.error(ERROR_MESSAGE_PROCESSING_FAILED_UNRECOVERABLE, loggedEvent, metadata);
      throw unrecoverableException;
    } catch (final ExceededBatchRecordSizeException exceededBatchRecordSizeException) {
      if (processedCommandsCount > 0) {
        LOG.warn(
            WARN_MESSAGE_BATCH_PROCESSING_RETRY,
            processedCommandsCount,
            maxCommandsInBatch,
            exceededBatchRecordSizeException);
        processingMetrics.countRetry();
        onError(exceededBatchRecordSizeException, () -> processCommand(loggedEvent));
      } else {
        onError(
            exceededBatchRecordSizeException,
            () -> {
              errorHandlingInTransaction(exceededBatchRecordSizeException);
              writeRecords();
            });
      }
    } catch (final Exception e) {
      onError(
          e,
          () -> {
            errorHandlingInTransaction(e);
            writeRecords();
          });
    }
  }

  /**
   * Finalize the command processing, which includes certain clean-up tasks, like mark the command
   * as processed and reset transient processing state, etc.
   *
   * <p>Should be called after processing or error handling is done.
   */
  private void finalizeCommandProcessing() {
    lastProcessedPositionState.markAsProcessed(typedCommand.getPosition());
    processedCommandsCount = 0;
  }

  /**
   * Starts the batch processing with the given initial command and iterates over ProcessingResult
   * and applies all follow-up commands until the command limit is reached or no more follow-up
   * commands are created.
   */
  private void batchProcessing(final TypedRecord<?> initialCommand) {
    // propagate the operation reference from the initial command to the processingResultBuilder to
    // be appended to the followup events
    final var processingResultBuilder =
        new BufferedProcessingResultBuilder(
            logStreamWriter::canWriteEvents, initialCommand.getOperationReference());
    var lastProcessingResultSize = 0;

    // It might be that we reached the batch size limit during processing a command.
    // We rolled back the transaction and processing result and retried the processing.
    // We know that we can process until the last processed commands count, which is why we set it
    // as our processing batch limit, in order to handle the commands afterwards as own batch.
    final var currentProcessingBatchLimit =
        processedCommandsCount > 0 ? processedCommandsCount : maxCommandsInBatch;
    processedCommandsCount = 0;
    pendingWrites = new ArrayList<>();
    pendingResponses = Collections.newSetFromMap(new IdentityHashMap<>(2));
    final var pendingCommands = new ArrayDeque<TypedRecord<?>>();
    pendingCommands.addLast(initialCommand);

    while (!pendingCommands.isEmpty() && processedCommandsCount < currentProcessingBatchLimit) {

      final var command = pendingCommands.removeFirst();

      currentProcessor =
          recordProcessors.stream()
              .filter(p -> p.accepts(command.getValueType()))
              .findFirst()
              .orElseThrow(() -> NoSuchProcessorException.forRecord(command));

      currentProcessingResult = currentProcessor.process(command, processingResultBuilder);

      final BatchProcessingStepResult batchProcessingStepResult =
          collectBatchProcessingStepResult(
              currentProcessingResult,
              lastProcessingResultSize,
              // +1 since we already need include the current command in the calculation
              pendingCommands.size() + processedCommandsCount + 1,
              currentProcessingBatchLimit);

      pendingCommands.addAll(batchProcessingStepResult.toProcess());
      pendingWrites.addAll(batchProcessingStepResult.toWrite());
      currentProcessingResult.getProcessingResponse().ifPresent(pendingResponses::add);

      lastProcessingResultSize = currentProcessingResult.getRecordBatch().entries().size();
      processedCommandsCount++;
      processingMetrics.commandsProcessed();
    }
  }

  /**
   * Collects from the given processing result the commands which should be processed further, and
   * the records which should be written to the log.
   *
   * @param processingResult the processing result of the last processed command
   * @param lastProcessingResultSize the size of the processing result before processing the last
   *     command
   * @param currentBatchSize the current batch size (only commands counted), includes already
   *     processed and pending commands
   * @return the result of the current batch processing step, which contains the next to processed
   *     commands and the records which should be written to the log
   */
  private BatchProcessingStepResult collectBatchProcessingStepResult(
      final ProcessingResult processingResult,
      final int lastProcessingResultSize,
      final int currentBatchSize,
      final int currentProcessingBatchLimit) {

    final var commandsToProcess = new ArrayList<TypedRecord<?>>();
    final var toWriteEntries = new ArrayList<LogAppendEntry>();

    processingResult.getRecordBatch().entries().stream()
        .skip(lastProcessingResultSize) // because the result builder is reused
        .forEachOrdered(
            entry -> {
              var toWriteEntry = entry;
              final int potentialBatchSize = currentBatchSize + commandsToProcess.size();
              if (entry.recordMetadata().getRecordType() == RecordType.COMMAND
                  && potentialBatchSize < currentProcessingBatchLimit) {
                commandsToProcess.add(
                    new UnwrittenRecord(
                        entry.key(),
                        context.getPartitionId(),
                        entry.recordValue(),
                        entry.recordMetadata()));
                toWriteEntry = LogAppendEntry.ofProcessed(entry);
              }
              toWriteEntries.add(toWriteEntry);
            });

    return new BatchProcessingStepResult(commandsToProcess, toWriteEntries);
  }

  private void onError(final Throwable error, final NextProcessingStep nextStep) {
    onErrorRetries++;
    switchErrorPhase();

    final ActorFuture<Boolean> retryFuture =
        updateStateRetryStrategy.runWithRetry(
            () -> {
              zeebeDbTransaction.rollback();
              return true;
            },
            abortCondition);

    actor.runOnCompletion(
        retryFuture,
        (bool, throwable) -> {
          if (throwable != null) {
            LOG.error(ERROR_MESSAGE_ROLLBACK_ABORTED, currentRecord, metadata, throwable);
          }
          try {
            if (tryExitOutOfErrorLoop(error)) {
              return;
            }
            nextStep.run();
          } catch (final Exception ex) {
            onError(ex, nextStep);
          }
        });
  }

  private boolean tryExitOutOfErrorLoop(final Throwable error) {
    try {
      // If in error loop and the processing record is a user command
      if (errorHandlingPhase == ErrorHandlingPhase.USER_COMMAND_PROCESSING_ERROR_FAILED) {
        // First try to reject with proper error message
        LOG.debug(ERROR_MESSAGE_HANDLING_PROCESSING_ERROR_FAILED, currentRecord, metadata, error);
        tryRejectingIfUserCommand(error.getMessage());
        return true;
      } else if (errorHandlingPhase == ErrorHandlingPhase.USER_COMMAND_REJECT_FAILED) {
        LOG.warn(ERROR_MESSAGE_HANDLING_PROCESSING_ERROR_FAILED, currentRecord, metadata, error);
        // try to reject with a generic error message
        tryRejectingIfUserCommand(
            String.format(
                "Expected to process command, but caught an exception. Check broker logs (partition %s) for details.",
                context.getPartitionId()));
        return true;
      }
    } catch (final Exception e) {
      // Unexpected error, so we just fail back to endless loop
      LOG.error(
          "Expected to write rejection for command '{} {}', but failed with unexpected error.",
          currentRecord,
          metadata,
          e);
      pendingResponses.clear();
      pendingWrites.clear();
    }
    return false;
  }

  private void startErrorLoop(final boolean isUserCommand) {
    if (errorHandlingPhase == ErrorHandlingPhase.NO_ERROR) {
      final var nextPhase =
          isUserCommand
              ? ErrorHandlingPhase.USER_COMMAND_PROCESSING_FAILED
              : ErrorHandlingPhase.PROCESSING_FAILED;
      updateErrorHandlingPhase(nextPhase);
    }
  }

  private void switchErrorPhase() {
    final var nextPhase =
        switch (errorHandlingPhase) {
          case NO_ERROR -> ErrorHandlingPhase.NO_ERROR; // First switch is explicit
          case PROCESSING_FAILED -> ErrorHandlingPhase.PROCESSING_ERROR_FAILED;
          case USER_COMMAND_PROCESSING_FAILED ->
              ErrorHandlingPhase.USER_COMMAND_PROCESSING_ERROR_FAILED;
          case USER_COMMAND_PROCESSING_ERROR_FAILED ->
              ErrorHandlingPhase.USER_COMMAND_REJECT_FAILED;
          case ErrorHandlingPhase.USER_COMMAND_REJECT_FAILED ->
              ErrorHandlingPhase.USER_COMMAND_REJECT_SIMPLE_REJECT_FAILED;
          case PROCESSING_ERROR_FAILED, USER_COMMAND_REJECT_SIMPLE_REJECT_FAILED -> {
            LOG.error(
                "Failed to process command '{} {}' retries. Entering endless error loop.",
                currentRecord,
                metadata);
            yield ErrorHandlingPhase.ENDLESS_ERROR_LOOP;
          }
          case ENDLESS_ERROR_LOOP -> ErrorHandlingPhase.ENDLESS_ERROR_LOOP;
        };
    updateErrorHandlingPhase(nextPhase);
  }

  private void tryRejectingIfUserCommand(final String errorMessage) {
    final var rejectionReason = errorMessage != null ? errorMessage : "";
    final ProcessingResultBuilder processingResultBuilder =
        new BufferedProcessingResultBuilder(
            logStreamWriter::canWriteEvents, typedCommand.getOperationReference());
    final var errorRecord = new ErrorRecord();
    errorRecord.initErrorRecord(
        new CommandRejectionException(rejectionReason), currentRecord.getPosition());

    final var recordMetadata =
        new RecordMetadata()
            .recordType(RecordType.EVENT)
            .valueType(ValueType.ERROR)
            .intent(ErrorIntent.CREATED)
            .recordVersion(RecordMetadata.DEFAULT_RECORD_VERSION)
            .rejectionType(RejectionType.NULL_VAL)
            .rejectionReason("")
            .operationReference(typedCommand.getOperationReference());
    processingResultBuilder.appendRecord(currentRecord.getKey(), errorRecord, recordMetadata);
    processingResultBuilder.withResponse(
        RecordType.COMMAND_REJECTION,
        typedCommand.getKey(),
        typedCommand.getIntent(),
        errorRecord,
        ValueType.ERROR,
        RejectionType.PROCESSING_ERROR,
        rejectionReason,
        typedCommand.getRequestId(),
        typedCommand.getRequestStreamId());
    currentProcessingResult = processingResultBuilder.build();

    pendingWrites = currentProcessingResult.getRecordBatch().entries();
    pendingResponses = currentProcessingResult.getProcessingResponse().stream().toList();

    finalizeCommandProcessing();
    writeRecords();
  }

  private void errorHandlingInTransaction(final Throwable processingException) throws Exception {
    startErrorLoop(typedCommand.hasRequestMetadata());
    zeebeDbTransaction = transactionContext.getCurrentTransaction();
    zeebeDbTransaction.run(
        () -> {
          final ProcessingResultBuilder processingResultBuilder =
              new BufferedProcessingResultBuilder(
                  logStreamWriter::canWriteEvents, typedCommand.getOperationReference());
          currentProcessingResult =
              currentProcessor.onProcessingError(
                  processingException, typedCommand, processingResultBuilder);
          pendingWrites = currentProcessingResult.getRecordBatch().entries();
          pendingResponses = currentProcessingResult.getProcessingResponse().stream().toList();
          // we need to mark the command as processed, even if the processing failed
          // otherwise we might replay the events, which have been written during
          // #onProcessingError again on restart
          finalizeCommandProcessing();
        });
  }

  private ActorFuture<Boolean> writeWithRetryAsync() {
    final var sourceRecordPosition = typedCommand.getPosition();

    final ActorFuture<Boolean> writeFuture;
    if (currentProcessingResult.isEmpty()) {
      // we skipped the processing entirely; we have no results
      notifySkippedListener(currentRecord);
      processingMetrics.eventSkipped();
      writeFuture = CompletableActorFuture.completed(true);
    } else if (pendingWrites.isEmpty()) {
      // we might have nothing to write but likely something to send as response
      // means we will not mark the record as skipped
      writeFuture = CompletableActorFuture.completed(true);
    } else {
      writeFuture =
          writeRetryStrategy.runWithRetry(
              () -> {
                final var writeResult =
                    logStreamWriter.tryWrite(
                        WriteContext.processingResult(), pendingWrites, sourceRecordPosition);
                if (writeResult.isRight()) {
                  writtenPosition = writeResult.get();
                  return true;
                } else {
                  return false;
                }
              },
              abortCondition);
    }
    return writeFuture;
  }

  private void writeRecords() {
    final ActorFuture<Boolean> writeFuture = writeWithRetryAsync();
    actor.runOnCompletion(
        writeFuture,
        (bool, t) -> {
          if (t != null) {
            LOG.error(ERROR_MESSAGE_WRITE_RECORD_ABORTED, currentRecord, metadata, t);
            onError(
                t,
                () -> {
                  errorHandlingInTransaction(t);
                  writeRecords();
                });
          } else {
            // We write various type of records. The positions are always increasing and
            // incremented by 1 for one record (even in a batch), so we can count the amount
            // of written records via the lastWritten and now written position.
            final var amount = writtenPosition - lastWrittenPosition;
            processingMetrics.recordsWritten(amount);
            updateState();
          }
        });
  }

  private void updateState() {
    final ActorFuture<Boolean> retryFuture =
        updateStateRetryStrategy.runWithRetry(
            () -> {
              zeebeDbTransaction.commit();
              lastSuccessfulProcessedRecordPosition = currentRecord.getPosition();
              processingMetrics.setLastProcessedPosition(lastSuccessfulProcessedRecordPosition);
              lastWrittenPosition = writtenPosition;
              return true;
            },
            abortCondition);

    actor.runOnCompletion(
        retryFuture,
        (bool, throwable) -> {
          if (throwable != null) {
            LOG.error(ERROR_MESSAGE_UPDATE_STATE_FAILED, currentRecord, metadata, throwable);
            onError(
                throwable,
                () -> {
                  errorHandlingInTransaction(throwable);
                  updateState();
                });
          } else {
            scheduledCommandCache.remove(metadata.getIntent(), currentRecord.getKey());
            executeSideEffects();
          }
        });
  }

  private void executeSideEffects() {
    final ActorFuture<Boolean> retryFuture =
        sideEffectsRetryStrategy.runWithRetry(
            () -> {
              // TODO refactor this into two parallel tasks, which are then combined, and on the
              // completion of which the process continues
              for (final var processingResponse : pendingResponses) {
                final var responseWriter = context.getCommandResponseWriter();

                final var responseValue = processingResponse.responseValue();
                final var recordMetadata = responseValue.recordMetadata();
                responseWriter
                    .intent(recordMetadata.getIntent())
                    .key(responseValue.key())
                    .recordType(recordMetadata.getRecordType())
                    .rejectionReason(BufferUtil.wrapString(recordMetadata.getRejectionReason()))
                    .rejectionType(recordMetadata.getRejectionType())
                    .partitionId(context.getPartitionId())
                    .valueType(recordMetadata.getValueType())
                    .valueWriter(responseValue.recordValue())
                    .tryWriteResponse(
                        processingResponse.requestStreamId(), processingResponse.requestId());
              }
              return executePostCommitTasks();
            },
            abortCondition);

    actor.runOnCompletion(
        retryFuture,
        (bool, throwable) -> {
          if (throwable != null) {
            LOG.error(
                ERROR_MESSAGE_EXECUTE_SIDE_EFFECT_ABORTED, currentRecord, metadata, throwable);
          }

          notifyProcessedListener(typedCommand);

          // observe the processing duration
          processingTimer.close();

          // continue with next record
          markProcessingCompleted();
          actor.submit(this::tryToReadNextRecord);
        });
  }

  private boolean executePostCommitTasks() {
    try (final var timer = processingMetrics.startBatchProcessingPostCommitTasksTimer()) {
      return currentProcessingResult.executePostCommitTasks();
    }
  }

  private void notifyProcessedListener(final TypedRecord processedRecord) {
    try {
      streamProcessorListener.onProcessed(processedRecord);
    } catch (final Exception e) {
      LOG.error(NOTIFY_PROCESSED_LISTENER_ERROR_MESSAGE, processedRecord, e);
    }
  }

  private void notifySkippedListener(final LoggedEvent skippedRecord) {
    try {
      streamProcessorListener.onSkipped(skippedRecord);
    } catch (final Exception e) {
      LOG.error(NOTIFY_SKIPPED_LISTENER_ERROR_MESSAGE, skippedRecord, metadata, e);
    }
  }

  public long getLastSuccessfulProcessedRecordPosition() {
    return lastSuccessfulProcessedRecordPosition;
  }

  public long getLastWrittenPosition() {
    return lastWrittenPosition;
  }

  public boolean isMakingProgress() {
    return errorHandlingPhase != ErrorHandlingPhase.ENDLESS_ERROR_LOOP;
  }

  public void startProcessing(final LastProcessingPositions lastProcessingPositions) {
    // Replay ends at the end of the log and returns the lastSourceRecordPosition
    // which is equal to the last processed position
    // we need to seek to the next record after that position where the processing should start
    // Be aware on processing we ignore events, so we will process the next command
    final var lastProcessedPosition = lastProcessingPositions.getLastProcessedPosition();
    logStreamReader.seekToNextEvent(lastProcessedPosition);
    if (lastSuccessfulProcessedRecordPosition == StreamProcessor.UNSET_POSITION) {
      lastSuccessfulProcessedRecordPosition = lastProcessedPosition;
    }

    if (lastWrittenPosition == StreamProcessor.UNSET_POSITION) {
      lastWrittenPosition = lastProcessingPositions.getLastWrittenPosition();
    }

    actor.submit(this::tryToReadNextRecord);
  }

  private void updateErrorHandlingPhase(final ErrorHandlingPhase errorHandlingPhase) {
    this.errorHandlingPhase = errorHandlingPhase;
    processingMetrics.errorHandlingPhase(errorHandlingPhase);
  }

  private record BatchProcessingStepResult(
      List<TypedRecord<?>> toProcess, List<LogAppendEntry> toWrite) {}

  @FunctionalInterface
  private interface NextProcessingStep {
    void run() throws Exception;
  }

  public enum ErrorHandlingPhase {
    NO_ERROR,
    // external commands failed in processRecord
    USER_COMMAND_PROCESSING_FAILED,
    // internal commands and events failed in processRecord
    PROCESSING_FAILED,
    // internal commands and events failed when handling the error from processing failure
    PROCESSING_ERROR_FAILED,
    // external commands failed when handling the error from processing failure
    USER_COMMAND_PROCESSING_ERROR_FAILED,
    // external commands failed when trying to reject with a proper error message
    USER_COMMAND_REJECT_FAILED,
    // external commands failed when trying to reject with a generic error message
    USER_COMMAND_REJECT_SIMPLE_REJECT_FAILED,
    // All attempted error handling failed.
    ENDLESS_ERROR_LOOP
  }
}
