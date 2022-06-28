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
import io.camunda.zeebe.engine.state.EventApplier;
import io.camunda.zeebe.engine.state.ZeebeDbState;
import io.camunda.zeebe.engine.state.mutable.MutableZeebeState;
import io.camunda.zeebe.logstreams.impl.Loggers;
import java.util.List;
import java.util.function.Function;
import org.slf4j.Logger;

public final class Engine implements StreamProcessorLifecycleAware {

  private static final Logger LOG = Loggers.PROCESSOR_LOGGER;

  private static final String ERROR_MESSAGE_ON_EVENT_FAILED_SKIP_EVENT =
      "Expected to find processor for record '{}', but caught an exception. Skip this record.";

  private RecordProcessorMap recordProcessorMap;

  private final TypedRecordProcessorFactory typedRecordProcessorFactory;
  private final List<StreamProcessorLifecycleAware> lifecycleAwareListeners;
  private final ZeebeDb zeebeDb;
  private final Function<MutableZeebeState, EventApplier> eventApplierFactory;
  private ZeebeDbState zeebeState;
  private ProcessingContext context;
  private EventApplier eventApplier;

  public Engine(final StreamProcessorBuilder processorBuilder) {
    typedRecordProcessorFactory = processorBuilder.getTypedRecordProcessorFactory();
    lifecycleAwareListeners = processorBuilder.getLifecycleListeners();
    zeebeDb = processorBuilder.getZeebeDb();
    eventApplierFactory = processorBuilder.getEventApplierFactory();
  }

  public void init(final ProcessingContext context) {
    this.context = context;
    final TransactionContext transactionContext = zeebeDb.createContext();

    final var partitionId = context.getLogStream().getPartitionId();
    zeebeState = new ZeebeDbState(partitionId, zeebeDb, transactionContext);

    context.transactionContext(transactionContext);
    context.zeebeState(zeebeState);
    eventApplier = eventApplierFactory.apply(zeebeState);
    context.eventApplier(eventApplier);

    final TypedRecordProcessors typedRecordProcessors =
        typedRecordProcessorFactory.createProcessors(context);

    lifecycleAwareListeners.addAll(typedRecordProcessors.getLifecycleListeners());
    recordProcessorMap = typedRecordProcessors.getRecordProcessorMap();
    recordProcessorMap.values().forEachRemaining(lifecycleAwareListeners::add);
  }

  private TypedRecordProcessor<?> chooseNextProcessor(final TypedRecord<?> command) {
    TypedRecordProcessor<?> typedRecordProcessor = null;

    try {
      typedRecordProcessor =
          recordProcessorMap.get(
              command.getRecordType(), command.getValueType(), command.getIntent().value());
    } catch (final Exception e) {
      LOG.error(ERROR_MESSAGE_ON_EVENT_FAILED_SKIP_EVENT, command, e);
    }

    return typedRecordProcessor;
  }

  public void apply(final TypedRecord typedEvent) {
    eventApplier.applyState(typedEvent.getKey(), typedEvent.getIntent(), typedEvent.getValue());
  }

  public ProcessingResult process(final TypedRecord typedCommand) {
    final TypedRecordProcessor<?> currentProcessor = chooseNextProcessor(typedCommand);
    if (currentProcessor == null) {

      return ProcessingResult.empty();
    }

    final boolean isNotOnBlacklist = !zeebeState.getBlackListState().isOnBlacklist(typedCommand);
    if (isNotOnBlacklist) {
      // todo remove the writers
      currentProcessor.processRecord(
          typedCommand, context.getWriters().response(), context.getLogStreamWriter());
    }

    return new ProcessingResult();
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
    lifecycleAwareListeners.forEach(StreamProcessorLifecycleAware::onResumed);
  }
}
