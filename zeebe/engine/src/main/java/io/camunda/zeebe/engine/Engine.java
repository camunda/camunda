/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine;

import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.zeebe.engine.processing.streamprocessor.RecordProcessorMap;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor.ProcessingError;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessorContextImpl;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessorFactory;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.EventApplier;
import io.camunda.zeebe.engine.state.appliers.EventAppliers;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.processing.DbBannedInstanceState;
import io.camunda.zeebe.engine.state.routing.RoutingInfo;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.error.ErrorRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ErrorIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceBatchIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceRelatedIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRelated;
import io.camunda.zeebe.stream.api.ProcessingResult;
import io.camunda.zeebe.stream.api.ProcessingResultBuilder;
import io.camunda.zeebe.stream.api.RecordProcessor;
import io.camunda.zeebe.stream.api.RecordProcessorContext;
import io.camunda.zeebe.stream.api.records.ExceededBatchRecordSizeException;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.EnumSet;
import java.util.Objects;
import java.util.function.Supplier;
import org.slf4j.Logger;

public class Engine implements RecordProcessor {

  private static final Logger LOG = Loggers.PROCESS_PROCESSOR_LOGGER;
  private static final String ERROR_MESSAGE_PROCESSOR_NOT_FOUND =
      "Expected to find processor for record '{}', but caught an exception. Skip this record.";
  private static final String ERROR_MESSAGE_PROCESSING_EXCEPTION_OCCURRED =
      "Expected to process record '%s' without errors, but exception occurred with message '%s'.";

  private static final EnumSet<ValueType> SUPPORTED_VALUETYPES =
      EnumSet.range(ValueType.JOB, ValueType.SCALE);

  private EventApplier eventApplier;
  private RecordProcessorMap recordProcessorMap;
  private MutableProcessingState processingState;

  private final ErrorRecord errorRecord = new ErrorRecord();

  private final ProcessingResultBuilderMutex resultBuilderMutex =
      new ProcessingResultBuilderMutex();

  private Writers writers;
  private final TypedRecordProcessorFactory typedRecordProcessorFactory;
  private final EngineConfiguration config;
  private final SecurityConfiguration securityConfig;

  public Engine(
      final TypedRecordProcessorFactory typedRecordProcessorFactory,
      final EngineConfiguration config,
      final SecurityConfiguration securityConfig) {
    this.typedRecordProcessorFactory = typedRecordProcessorFactory;
    this.config = config;
    this.securityConfig = securityConfig;
  }

  /**
   * Returns whether scaling is currently in progress. This can be used to determine if certain
   * operations like checkpoint creation should be blocked.
   *
   * @return true if scaling is in progress, false otherwise
   */
  public boolean isScalingInProgress() {
    if (processingState == null || processingState.getRoutingState() == null) {
      return false;
    }

    final var routingState = processingState.getRoutingState();
    if (!routingState.isInitialized()) {
      return false;
    }

    // Scaling is in progress if desired partitions differ from current partitions
    final var currentPartitions = routingState.currentPartitions();
    final var desiredPartitions = routingState.desiredPartitions();

    return !currentPartitions.equals(desiredPartitions);
  }

  /**
   * Returns the current partition count from routing information. If routing state is not
   * initialized, returns the fallback partition count.
   *
   * @param fallBackPartitionCount the fallback partition count to use if dynamic routing state is
   *     not initialized
   * @return the current partition count
   */
  public int getCurrentPartitionCount(final int fallBackPartitionCount) {
    return RoutingInfo.dynamic(
            processingState.getRoutingState(),
            RoutingInfo.forStaticPartitions(fallBackPartitionCount))
        .partitions()
        .size();
  }

  @Override
  public void init(final RecordProcessorContext recordProcessorContext) {
    eventApplier = new EventAppliers();
    writers = new Writers(resultBuilderMutex, eventApplier);

    final var typedProcessorContext =
        new TypedRecordProcessorContextImpl(
            recordProcessorContext, writers, config, securityConfig);
    processingState = typedProcessorContext.getProcessingState();
    writers.setKeyValidator(processingState.getKeyGenerator());

    ((EventAppliers) eventApplier).registerEventAppliers(processingState);
    final TypedRecordProcessors typedRecordProcessors =
        typedRecordProcessorFactory.createProcessors(typedProcessorContext);

    recordProcessorContext.addLifecycleListeners(typedRecordProcessors.getLifecycleListeners());
    recordProcessorMap = typedRecordProcessors.getRecordProcessorMap();

    recordProcessorContext
        .getClock()
        .applyModification(processingState.getClockState().getModification());
  }

  @Override
  public boolean accepts(final ValueType valueType) {
    return SUPPORTED_VALUETYPES.contains(valueType);
  }

  @Override
  public void replay(final TypedRecord event) {
    eventApplier.applyState(
        event.getKey(), event.getIntent(), event.getValue(), event.getRecordVersion());
  }

  @Override
  public ProcessingResult process(
      final TypedRecord record, final ProcessingResultBuilder processingResultBuilder) {

    try (final var scope = new ProcessingResultBuilderScope(processingResultBuilder)) {
      TypedRecordProcessor<?> currentProcessor = null;

      final var typedCommand = (TypedRecord<?>) record;
      try {
        currentProcessor =
            recordProcessorMap.get(
                typedCommand.getRecordType(),
                typedCommand.getValueType(),
                typedCommand.getIntent().value());
      } catch (final Exception e) {
        LOG.error(ERROR_MESSAGE_PROCESSOR_NOT_FOUND, typedCommand, e);
      }

      if (currentProcessor == null) {
        return processingResultBuilder.build();
      }

      if (shouldProcessCommand(typedCommand)) {
        if (currentProcessor.shouldProcessResultsInSeparateBatches()) {
          processingResultBuilder.withProcessInASeparateBatch();
        }

        currentProcessor.processRecord(record, processingResultBuilder);
      }
    }
    return processingResultBuilder.build();
  }

  @Override
  public ProcessingResult onProcessingError(
      final Throwable processingException,
      final TypedRecord record,
      final ProcessingResultBuilder processingResultBuilder) {
    try (final var scope = new ProcessingResultBuilderScope(processingResultBuilder)) {

      final var typedCommand = (TypedRecord<?>) record;
      TypedRecordProcessor<?> processor = null;
      try {
        processor =
            recordProcessorMap.get(
                typedCommand.getRecordType(),
                typedCommand.getValueType(),
                typedCommand.getIntent().value());
      } catch (final Exception e) {
        LOG.error(ERROR_MESSAGE_PROCESSOR_NOT_FOUND, typedCommand, e);
      }

      final var error =
          processor == null
              ? ProcessingError.UNEXPECTED_ERROR
              : processor.tryHandleError(record, processingException);

      if (error == ProcessingError.UNEXPECTED_ERROR) {
        final var errorRecord = getRejectionRecord(record);
        handleUnexpectedError(processingException, errorRecord);
      }
    }
    return processingResultBuilder.build();
  }

  private boolean shouldProcessCommand(final TypedRecord<?> typedCommand) {
    // There is no ban check needed if the intent is not instance related
    // nor if the intent is to create new instances, which can't be banned yet
    final Intent intent = typedCommand.getIntent();
    final boolean noBanCheckNeeded =
        !(intent instanceof ProcessInstanceRelatedIntent)
            || intent instanceof ProcessInstanceCreationIntent;

    if (noBanCheckNeeded) {
      return true;
    }

    final boolean banned = processingState.getBannedInstanceState().isBanned(typedCommand);

    if (!banned) {
      return true;
    }

    // Commands allowed to be processed on banned instances
    return intent == ProcessInstanceIntent.CANCEL
        || intent == ProcessInstanceIntent.TERMINATE_ELEMENT
        || intent == ProcessInstanceBatchIntent.TERMINATE;
  }

  private void handleUnexpectedError(
      final Throwable processingException, final TypedRecord record) {
    final String errorMessage =
        String.format(
            ERROR_MESSAGE_PROCESSING_EXCEPTION_OCCURRED, record, processingException.getMessage());
    LOG.error(errorMessage, processingException);

    if (processingException instanceof ExceededBatchRecordSizeException) {
      // Rejection reason is left empty here. This is because we need to make sure we can write the
      // rejection. The record itself does not exceed the batch record size, but adding a reason
      // could cause it to cross the limit. When this happens the engine would reach an
      // exception-loop.
      writers.rejection().appendRejection(record, RejectionType.EXCEEDED_BATCH_RECORD_SIZE, "");
      writers
          .response()
          .writeRejectionOnCommand(record, RejectionType.EXCEEDED_BATCH_RECORD_SIZE, "");
    } else {
      writers.rejection().appendRejection(record, RejectionType.PROCESSING_ERROR, errorMessage);
      writers
          .response()
          .writeRejectionOnCommand(
              record,
              RejectionType.PROCESSING_ERROR,
              """
                Expected to process command %d (%s.%s) without errors, but unexpected error occurred. \
                Check your broker logs (partition %d), or ask your operator, for more details."""
                  .formatted(
                      record.getKey(),
                      record.getValueType(),
                      record.getIntent(),
                      record.getPartitionId()));
    }
    errorRecord.initErrorRecord(processingException, record.getPosition());

    if (DbBannedInstanceState.shouldBeBanned(record.getIntent())) {
      if (record.getValue() instanceof ProcessInstanceRelated) {
        final long processInstanceKey =
            ((ProcessInstanceRelated) record.getValue()).getProcessInstanceKey();
        errorRecord.setProcessInstanceKey(processInstanceKey);
      }

      writers.state().appendFollowUpEvent(record.getKey(), ErrorIntent.CREATED, errorRecord);
    }
  }

  /**
   * This method removes redundant information from rejected records in order to avoid the {@link
   * ExceededBatchRecordSizeException} when writing the rejection event.
   *
   * <ul>
   *   <li>Commands of type {@link DeploymentRecord}: for those records the resources information
   *       are not necessary
   * </ul>
   *
   * @param record the record to transform
   * @return the {@link TypedRecord} with only necessary information
   */
  private TypedRecord getRejectionRecord(final TypedRecord record) {
    if (record.getValue() instanceof final DeploymentRecord deploymentRecord) {
      deploymentRecord.resetResources();
    }
    return record;
  }

  private static final class ProcessingResultBuilderMutex
      implements Supplier<ProcessingResultBuilder> {

    private ProcessingResultBuilder resultBuilder;

    private void setResultBuilder(final ProcessingResultBuilder resultBuilder) {
      this.resultBuilder = Objects.requireNonNull(resultBuilder);
    }

    private void unsetResultBuilder() {
      resultBuilder = null;
    }

    @Override
    public ProcessingResultBuilder get() {
      if (resultBuilder == null) {
        throw new IllegalStateException("Attempt to retrieve resultBuilder out of scope.");
      }
      return resultBuilder;
    }
  }

  private final class ProcessingResultBuilderScope implements AutoCloseable {

    private ProcessingResultBuilderScope(final ProcessingResultBuilder processingResultBuilder) {
      resultBuilderMutex.setResultBuilder(processingResultBuilder);
    }

    @Override
    public void close() {
      resultBuilderMutex.unsetResultBuilder();
    }
  }
}
