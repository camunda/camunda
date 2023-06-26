/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.ProcessingDbState;
import io.camunda.zeebe.engine.state.ScheduledTaskDbState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.stream.api.InterPartitionCommandSender;
import io.camunda.zeebe.stream.api.RecordProcessorContext;
import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;
import java.util.function.Supplier;

public class TypedRecordProcessorContextImpl implements TypedRecordProcessorContext {

  private final int partitionId;
  private final ProcessingScheduleService scheduleService;
  private final ProcessingDbState processingState;
  private final ZeebeDb zeebeDb;
  private final Writers writers;
  private final InterPartitionCommandSender partitionCommandSender;
  private final EngineConfiguration config;

  public TypedRecordProcessorContextImpl(
      final RecordProcessorContext context,
      final Writers writers,
      final EngineConfiguration config) {
    this.partitionId = context.getPartitionId();
    this.scheduleService = context.getScheduleService();
    this.zeebeDb = context.getZeebeDb();
    this.processingState =
        new ProcessingDbState(
            partitionId, zeebeDb, context.getTransactionContext(), context.getKeyGenerator());
    this.writers = writers;
    this.partitionCommandSender = context.getPartitionCommandSender();
    this.config = config;
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
  public MutableProcessingState getProcessingState() {
    return processingState;
  }

  @Override
  public Writers getWriters() {
    return writers;
  }

  @Override
  public InterPartitionCommandSender getPartitionCommandSender() {
    return partitionCommandSender;
  }

  @Override
  public Supplier<ScheduledTaskDbState> getScheduledTaskDbStateFactory() {
    return () -> new ScheduledTaskDbState(zeebeDb, zeebeDb.createContext());
  }

  @Override
  public EngineConfiguration getConfig() {
    return config;
  }
}
