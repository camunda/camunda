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
import io.camunda.zeebe.engine.api.ProcessingScheduleService;
import io.camunda.zeebe.engine.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.engine.processing.streamprocessor.StreamProcessorListener;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessorFactory;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.LegacyTypedStreamWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.EventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableZeebeState;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

public final class EngineContext {

  private final int partitionId;
  private final ProcessingScheduleService scheduleService;
  private final ZeebeDb zeebeDb;
  private final TransactionContext transactionContext;
  private final LegacyTypedStreamWriter streamWriter;
  private final TypedResponseWriter responseWriter;
  private final Function<MutableZeebeState, EventApplier> eventApplierFactory;
  private final TypedRecordProcessorFactory typedRecordProcessorFactory;
  private List<StreamProcessorLifecycleAware> lifecycleListeners = Collections.EMPTY_LIST;
  private StreamProcessorListener streamProcessorListener;
  private Writers writers;

  public EngineContext(
      final int partitionId,
      final ProcessingScheduleService scheduleService,
      final ZeebeDb zeebeDb,
      final TransactionContext transactionContext,
      final LegacyTypedStreamWriter streamWriter,
      final TypedResponseWriter responseWriter,
      final Function<MutableZeebeState, EventApplier> eventApplierFactory,
      final TypedRecordProcessorFactory typedRecordProcessorFactory) {
    this.partitionId = partitionId;
    this.scheduleService = scheduleService;
    this.zeebeDb = zeebeDb;
    this.transactionContext = transactionContext;
    this.streamWriter = streamWriter;
    this.responseWriter = responseWriter;
    this.eventApplierFactory = eventApplierFactory;
    this.typedRecordProcessorFactory = typedRecordProcessorFactory;
  }

  public int getPartitionId() {
    return partitionId;
  }

  public ProcessingScheduleService getScheduleService() {
    return scheduleService;
  }

  public ZeebeDb getZeebeDb() {
    return zeebeDb;
  }

  public TransactionContext getTransactionContext() {
    return transactionContext;
  }

  public LegacyTypedStreamWriter getStreamWriterProxy() {
    return streamWriter;
  }

  public TypedResponseWriter getTypedResponseWriter() {
    return responseWriter;
  }

  public Function<MutableZeebeState, EventApplier> getEventApplierFactory() {
    return eventApplierFactory;
  }

  public TypedRecordProcessorFactory getTypedRecordProcessorFactory() {
    return typedRecordProcessorFactory;
  }

  public List<StreamProcessorLifecycleAware> getLifecycleListeners() {
    return lifecycleListeners;
  }

  public void setLifecycleListeners(final List<StreamProcessorLifecycleAware> lifecycleListeners) {
    this.lifecycleListeners = lifecycleListeners;
  }

  public StreamProcessorListener getStreamProcessorListener() {
    return streamProcessorListener;
  }

  public void setStreamProcessorListener(final StreamProcessorListener streamProcessorListener) {
    this.streamProcessorListener = streamProcessorListener;
  }

  public Writers getWriters() {
    return writers;
  }

  public void setWriters(final Writers writers) {
    this.writers = writers;
  }
}
