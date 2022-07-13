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
import io.camunda.zeebe.engine.processing.streamprocessor.RecordProcessorMap;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessorFactory;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.camunda.zeebe.engine.state.EventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableZeebeState;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

public class RecordProcessorContextImpl implements RecordProcessorContext {

  private final int partitionId;
  private final ProcessingScheduleService scheduleService;
  private final ZeebeDb zeebeDb;
  private final TransactionContext transactionContext;
  private final TypedStreamWriter streamWriter;
  private final TypedResponseWriter responseWriter;
  private final Function<MutableZeebeState, EventApplier> eventApplierFactory;
  private final TypedRecordProcessorFactory typedRecordProcessorFactory;
  private List<StreamProcessorLifecycleAware> lifecycleListeners = Collections.EMPTY_LIST;
  private RecordProcessorMap recordProcessorMap;

  public RecordProcessorContextImpl(
      final int partitionId,
      final ProcessingScheduleService scheduleService,
      final ZeebeDb zeebeDb,
      final TransactionContext transactionContext,
      final TypedStreamWriter streamWriter,
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
  public TypedStreamWriter getStreamWriterProxy() {
    return streamWriter;
  }

  @Override
  public TypedResponseWriter getTypedResponseWriter() {
    return responseWriter;
  }

  @Override
  public Function<MutableZeebeState, EventApplier> getEventApplierFactory() {
    return eventApplierFactory;
  }

  @Override
  public TypedRecordProcessorFactory getTypedRecordProcessorFactory() {
    return typedRecordProcessorFactory;
  }

  @Deprecated // will likely be moved to engine
  public List<StreamProcessorLifecycleAware> getLifecycleListeners() {
    return lifecycleListeners;
  }

  @Override
  public void setLifecycleListeners(final List<StreamProcessorLifecycleAware> lifecycleListeners) {
    this.lifecycleListeners = lifecycleListeners;
  }

  @Deprecated // will be moved to engine
  public RecordProcessorMap getRecordProcessorMap() {
    return recordProcessorMap;
  }

  @Override
  public void setRecordProcessorMap(final RecordProcessorMap recordProcessorMap) {
    this.recordProcessorMap = recordProcessorMap;
  }
}
