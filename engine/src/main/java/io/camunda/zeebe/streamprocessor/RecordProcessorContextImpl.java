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
import java.util.ArrayList;
import java.util.List;

public final class RecordProcessorContextImpl implements RecordProcessorContext {

  private final int partitionId;
  private final ProcessingScheduleService scheduleService;
  private final ZeebeDb zeebeDb;
  private final TransactionContext transactionContext;
  private final List<StreamProcessorLifecycleAware> lifecycleListeners = new ArrayList<>();
  private StreamProcessorListener streamProcessorListener;

  public RecordProcessorContextImpl(
      final int partitionId,
      final ProcessingScheduleService scheduleService,
      final ZeebeDb zeebeDb,
      final TransactionContext transactionContext) {
    this.partitionId = partitionId;
    this.scheduleService = scheduleService;
    this.zeebeDb = zeebeDb;
    this.transactionContext = transactionContext;
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
