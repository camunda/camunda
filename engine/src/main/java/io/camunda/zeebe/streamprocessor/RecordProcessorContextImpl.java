/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.streamprocessor;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.api.ProcessingScheduleService;
import io.camunda.zeebe.engine.api.RecordProcessorContext;
import io.camunda.zeebe.engine.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.engine.processing.streamprocessor.StreamProcessorListener;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.LegacyTypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.LegacyTypedStreamWriter;
import io.camunda.zeebe.engine.state.EventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableZeebeState;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class RecordProcessorContextImpl implements RecordProcessorContext {

  private final int partitionId;
  private final ProcessingScheduleService scheduleService;
  private final ZeebeDb zeebeDb;
  private final TransactionContext transactionContext;
  private final LegacyTypedStreamWriter streamWriter;
  private final LegacyTypedResponseWriter responseWriter;
  private final Function<MutableZeebeState, EventApplier> eventApplierFactory;
  private final List<StreamProcessorLifecycleAware> lifecycleListeners = new ArrayList<>();
  private StreamProcessorListener streamProcessorListener;

  public RecordProcessorContextImpl(
      final int partitionId,
      final ProcessingScheduleService scheduleService,
      final ZeebeDb zeebeDb,
      final TransactionContext transactionContext,
      final LegacyTypedStreamWriter streamWriter,
      final LegacyTypedResponseWriter responseWriter,
      final Function<MutableZeebeState, EventApplier> eventApplierFactory) {
    this.partitionId = partitionId;
    this.scheduleService = scheduleService;
    this.zeebeDb = zeebeDb;
    this.transactionContext = transactionContext;
    this.streamWriter = streamWriter;
    this.responseWriter = responseWriter;
    this.eventApplierFactory = eventApplierFactory;
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
  public ZeebeDb getZeebeDb() {
    return zeebeDb;
  }

  @Override
  public TransactionContext getTransactionContext() {
    return transactionContext;
  }

  @Override
  public LegacyTypedStreamWriter getStreamWriterProxy() {
    return streamWriter;
  }

  @Override
  public LegacyTypedResponseWriter getTypedResponseWriter() {
    return responseWriter;
  }

  @Override
  public Function<MutableZeebeState, EventApplier> getEventApplierFactory() {
    return eventApplierFactory;
  }

  @Override
  public List<StreamProcessorLifecycleAware> getLifecycleListeners() {
    return lifecycleListeners;
  }

  @Override
  public StreamProcessorListener getStreamProcessorListener() {
    return streamProcessorListener;
  }

  @Override
  public void setStreamProcessorListener(final StreamProcessorListener streamProcessorListener) {
    this.streamProcessorListener = streamProcessorListener;
  }

  @Override
  public void addLifecycleListeners(final List<StreamProcessorLifecycleAware> lifecycleListeners) {
    this.lifecycleListeners.addAll(lifecycleListeners);
  }
}
