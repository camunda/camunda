/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Builders;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.RecordBatchBuilderImpl;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.RecordsBuilderImpl;
import io.camunda.zeebe.engine.state.EventApplier;
import io.camunda.zeebe.engine.state.ZeebeDbState;
import io.camunda.zeebe.engine.state.mutable.MutableZeebeState;
import io.camunda.zeebe.logstreams.impl.Loggers;
import io.camunda.zeebe.protocol.impl.record.value.error.ErrorRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ErrorIntent;
import java.util.List;
import java.util.function.Function;
import org.slf4j.Logger;

public final class Engine implements StreamProcessor {

  private static final Logger LOG = Loggers.PROCESSOR_LOGGER;

  private static final String PROCESSING_ERROR_MESSAGE =
      "Expected to process record '%s' without errors, but exception occurred with message '%s'.";
  private static final String ERROR_MESSAGE_ON_EVENT_FAILED_SKIP_EVENT =
      "Expected to find processor for record '{}', but caught an exception. Skip this record.";

  private RecordProcessorMap recordProcessorMap;

  private final TypedRecordProcessorFactory typedRecordProcessorFactory;
  private final List<StreamProcessorLifecycleAware> lifecycleAwareListeners;
  private final ZeebeDb zeebeDb;
  private final Function<MutableZeebeState, EventApplier> eventApplierFactory;
  private final ErrorRecord errorRecord = new ErrorRecord();

  private ZeebeDbState zeebeState;
  private EventApplier eventApplier;
  private RecordsBuilderImpl recordsBuilder;
  private Builders builders;

  public Engine(final StreamProcessorBuilder processorBuilder) {
    typedRecordProcessorFactory = processorBuilder.getTypedRecordProcessorFactory();
    lifecycleAwareListeners = processorBuilder.getLifecycleListeners();
    zeebeDb = processorBuilder.getZeebeDb();
    eventApplierFactory = processorBuilder.getEventApplierFactory();
  }

  @Override
  public void init(final EngineProcessingContext context) {
    final TransactionContext transactionContext = zeebeDb.createContext();

    final var partitionId = context.getPartitionId();
    zeebeState = new ZeebeDbState(partitionId, zeebeDb, transactionContext);

    context.transactionContext(transactionContext);
    context.zeebeState(zeebeState);
    eventApplier = eventApplierFactory.apply(zeebeState);
    recordsBuilder = new RecordsBuilderImpl(new RecordBatchBuilderImpl());
    context.initBuilders(recordsBuilder, eventApplier);
    final TypedRecordProcessors typedRecordProcessors =
        typedRecordProcessorFactory.createProcessors(context);

    lifecycleAwareListeners.addAll(typedRecordProcessors.getLifecycleListeners());
    recordProcessorMap = typedRecordProcessors.getRecordProcessorMap();
    recordProcessorMap.values().forEachRemaining(lifecycleAwareListeners::add);
    builders = context.getWriters();
  }

  @Override
  public void apply(final TypedRecord typedEvent) {
    eventApplier.applyState(typedEvent.getKey(), typedEvent.getIntent(), typedEvent.getValue());
  }

  @Override
  public ProcessingResult process(final TypedRecord typedCommand) {
    TypedRecordProcessor<?> currentProcessor = null;

    try {
      currentProcessor =
          recordProcessorMap.get(
              ((TypedRecord<?>) typedCommand).getRecordType(),
              ((TypedRecord<?>) typedCommand).getValueType(),
              ((TypedRecord<?>) typedCommand).getIntent().value());
    } catch (final Exception e) {
      LOG.error(ERROR_MESSAGE_ON_EVENT_FAILED_SKIP_EVENT, typedCommand, e);
    }

    if (currentProcessor == null) {
      return ProcessingResult.empty();
    }

    recordsBuilder.reset(); // to clean up the buffer
    final boolean isNotOnBlacklist = !zeebeState.getBlackListState().isOnBlacklist(typedCommand);
    if (isNotOnBlacklist) {
      currentProcessor.processRecord(typedCommand);
    }

    return new ProcessingResult(recordsBuilder);
  }

  @Override
  public ProcessingResult onProcessingError(
      final Throwable processingException, final TypedRecord typedCommand, final long position) {
    final String errorMessage =
        String.format(PROCESSING_ERROR_MESSAGE, typedCommand, processingException.getMessage());
    LOG.error(errorMessage, processingException);

    builders
        .rejection()
        .appendRejection(typedCommand, RejectionType.PROCESSING_ERROR, errorMessage);
    builders
        .response()
        .writeRejectionOnCommand(typedCommand, RejectionType.PROCESSING_ERROR, errorMessage);
    errorRecord.initErrorRecord(processingException, position);

    // I think we don't need this since the applier will do it.
    //    zeebeState
    //        .getBlackListState()
    //        .tryToBlacklist(typedCommand, errorRecord::setProcessInstanceKey);

    builders.state().appendFollowUpEvent(typedCommand.getKey(), ErrorIntent.CREATED, errorRecord);
    return ProcessingResult.empty();
  }

  @Override
  public void onRecovered(final ReadonlyProcessingContext context) {
    lifecycleAwareListeners.forEach(l -> l.onRecovered(context));
  }

  @Override
  public void onClose() {
    lifecycleAwareListeners.forEach(StreamProcessorLifecycleAware::onClose);
  }

  @Override
  public void onFailed() {
    lifecycleAwareListeners.forEach(StreamProcessorLifecycleAware::onFailed);
  }

  @Override
  public void onPaused() {
    // todo: should not called - engine doesn't need to be aware.
    lifecycleAwareListeners.forEach(StreamProcessorLifecycleAware::onPaused);
  }

  @Override
  public void onResumed() {
    // todo: should not called - engine doesn't need to be aware.
    // this can be removed after the schedule service is inserted
    lifecycleAwareListeners.forEach(StreamProcessorLifecycleAware::onResumed);
  }
}
