/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import io.camunda.zeebe.engine.api.InterPartitionCommandSender;
import io.camunda.zeebe.engine.api.ProcessingScheduleService;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.ProcessingDbState;
import io.camunda.zeebe.engine.state.ScheduledTaskDbState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;

public class TypedRecordProcessorContextImpl implements TypedRecordProcessorContext {

  private final int partitionId;
  private final ProcessingScheduleService scheduleService;
  private final ProcessingDbState processingState;
  private final ScheduledTaskDbState scheduledTaskDbState;
  private final Writers writers;
  private final InterPartitionCommandSender partitionCommandSender;

  public TypedRecordProcessorContextImpl(
      final int partitionId,
      final ProcessingScheduleService scheduleService,
      final ProcessingDbState processingState,
      final ScheduledTaskDbState scheduledTaskDbState,
      final Writers writers,
      final InterPartitionCommandSender partitionCommandSender) {
    this.partitionId = partitionId;
    this.scheduleService = scheduleService;
    this.processingState = processingState;
    this.scheduledTaskDbState = scheduledTaskDbState;
    this.writers = writers;
    this.partitionCommandSender = partitionCommandSender;
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
  public ScheduledTaskDbState getScheduledTaskDbState() {
    return scheduledTaskDbState;
  }
}
