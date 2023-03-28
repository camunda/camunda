/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.stream.impl;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.stream.api.InterPartitionCommandSender;
import io.camunda.zeebe.stream.api.RecordProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.stream.api.state.KeyGeneratorControls;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;

public final class RecordProcessorContextImpl implements RecordProcessorContext {

  private final int partitionId;
  private final ProcessingScheduleService scheduleService;
  private final ZeebeDb zeebeDb;
  private final TransactionContext transactionContext;
  private final List<StreamProcessorLifecycleAware> lifecycleListeners = new ArrayList<>();
  private final InterPartitionCommandSender partitionCommandSender;
  private final KeyGenerator keyGenerator;

  private final InstantSource clock;

  public RecordProcessorContextImpl(
      final int partitionId,
      final ProcessingScheduleService scheduleService,
      final ZeebeDb zeebeDb,
      final TransactionContext transactionContext,
      final InterPartitionCommandSender partitionCommandSender,
      final KeyGeneratorControls keyGeneratorControls,
      final InstantSource clock) {
    this.partitionId = partitionId;
    this.scheduleService = scheduleService;
    this.zeebeDb = zeebeDb;
    this.transactionContext = transactionContext;
    this.partitionCommandSender = partitionCommandSender;
    keyGenerator = keyGeneratorControls;
    this.clock = clock;
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
  public void addLifecycleListeners(final List<StreamProcessorLifecycleAware> lifecycleListeners) {
    this.lifecycleListeners.addAll(lifecycleListeners);
  }

  @Override
  public InterPartitionCommandSender getPartitionCommandSender() {
    return partitionCommandSender;
  }

  @Override
  public KeyGenerator getKeyGenerator() {
    return keyGenerator;
  }

  @Override
  public InstantSource getClock() {
    return clock;
  }
}
