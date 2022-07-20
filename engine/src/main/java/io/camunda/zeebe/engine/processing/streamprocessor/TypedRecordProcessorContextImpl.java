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
import io.camunda.zeebe.engine.api.ProcessingScheduleService;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.EventApplyingStateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.LegacyTypedStreamWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.EventApplier;
import io.camunda.zeebe.engine.state.ZeebeDbState;
import io.camunda.zeebe.engine.state.mutable.MutableZeebeState;
import java.util.function.Function;

public class TypedRecordProcessorContextImpl implements TypedRecordProcessorContext {

  private final int partitionId;
  private final ProcessingScheduleService scheduleService;
  private StreamProcessorListener streamProcessorListener;
  private final ZeebeDbState zeebeState;
  private final Writers writers;

  public TypedRecordProcessorContextImpl(
      final int partitionId,
      final ProcessingScheduleService scheduleService,
      final ZeebeDb zeebeDb,
      final TransactionContext transactionContext,
      final LegacyTypedStreamWriter streamWriterProxy,
      final Function<MutableZeebeState, EventApplier> eventApplierFactory,
      final TypedResponseWriter typedResponseWriter) {
    this.partitionId = partitionId;
    this.scheduleService = scheduleService;

    zeebeState = new ZeebeDbState(partitionId, zeebeDb, transactionContext);

    final var stateWriter =
        new EventApplyingStateWriter(streamWriterProxy, eventApplierFactory.apply(zeebeState));
    writers = new Writers(streamWriterProxy, stateWriter, typedResponseWriter);
  }

  @Override
  public int getPartitionId() {
    return partitionId;
  }

  @Override
  public ProcessingScheduleService getScheduleService() {
    return scheduleService;
  }

  @Override
  public MutableZeebeState getZeebeState() {
    return zeebeState;
  }

  @Override
  public Writers getWriters() {
    return writers;
  }

  @Override
  public TypedRecordProcessorContext listener(
      final StreamProcessorListener streamProcessorListener) {
    this.streamProcessorListener = streamProcessorListener;
    return this;
  }

  public StreamProcessorListener getStreamProcessorListener() {
    return streamProcessorListener;
  }
}
