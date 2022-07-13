/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.api.ErrorHandlingContext;
import io.camunda.zeebe.engine.api.ProcessingContext;
import io.camunda.zeebe.engine.api.ProcessingResult;
import io.camunda.zeebe.engine.api.RecordProcessor;
import io.camunda.zeebe.engine.api.RecordProcessorContext;
import io.camunda.zeebe.engine.api.TypedRecord;
import io.camunda.zeebe.engine.processing.streamprocessor.RecordProcessorMap;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessorContextImpl;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.camunda.zeebe.engine.state.EventApplier;
import io.camunda.zeebe.engine.state.ZeebeDbState;
import io.camunda.zeebe.engine.state.mutable.MutableZeebeState;
import java.util.function.Function;

public class Engine implements RecordProcessor {

  private final EventApplier eventApplier;

  public Engine(
      final int partitionId,
      final ZeebeDb zeebeDb,
      final Function<MutableZeebeState, EventApplier> eventApplierFactory) {

    final TransactionContext transactionContext = zeebeDb.createContext();
    final var zeebeState = new ZeebeDbState(partitionId, zeebeDb, transactionContext);
    eventApplier = eventApplierFactory.apply(zeebeState);
  }

  @Override
  public void init(final RecordProcessorContext recordProcessorContext) {
    final var typedProcessorContext =
        new TypedRecordProcessorContextImpl(
            recordProcessorContext.getPartitionId(),
            recordProcessorContext.getScheduleService(),
            recordProcessorContext.getZeebeDb(),
            recordProcessorContext.getTransactionContext(),
            recordProcessorContext.getStreamWriterProxy(),
            recordProcessorContext.getEventApplierFactory(),
            recordProcessorContext.getTypedResponseWriter());

    final TypedRecordProcessors typedRecordProcessors =
        recordProcessorContext
            .getTypedRecordProcessorFactory()
            .createProcessors(typedProcessorContext);

    recordProcessorContext.setLifecycleListeners(typedRecordProcessors.getLifecycleListeners());
    final RecordProcessorMap recordProcessorMap = typedRecordProcessors.getRecordProcessorMap();

    recordProcessorContext.setRecordProcessorMap(recordProcessorMap);
  }

  @Override
  public void replay(final TypedRecord event) {
    eventApplier.applyState(event.getKey(), event.getIntent(), event.getValue());
  }

  @Override
  public ProcessingResult process(
      final TypedRecord record, final ProcessingContext processingContext) {
    throw new IllegalStateException("Not yet implemented");
  }

  @Override
  public ProcessingResult onProcessingError(
      final Throwable processingException,
      final TypedRecord record,
      final ErrorHandlingContext errorHandlingContext) {
    throw new IllegalStateException("Not yet implemented");
  }
}
