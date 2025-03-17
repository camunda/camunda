/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.ProcessingDbState;
import io.camunda.zeebe.engine.state.ScheduledTaskDbState;
import io.camunda.zeebe.engine.state.immutable.ScheduledTaskState;
import io.camunda.zeebe.engine.state.message.TransientPendingSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.stream.api.InterPartitionCommandSender;
import io.camunda.zeebe.stream.api.RecordProcessorContext;
import io.camunda.zeebe.stream.api.StreamClock.ControllableStreamClock;
import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Objects;
import java.util.function.Supplier;

public class TypedRecordProcessorContextImpl implements TypedRecordProcessorContext {

  private final int partitionId;
  private final ProcessingScheduleService scheduleService;
  private final ProcessingDbState processingState;
  private final ZeebeDb zeebeDb;
  private final Writers writers;
  private final InterPartitionCommandSender partitionCommandSender;
  private final EngineConfiguration config;
  private final TransientPendingSubscriptionState transientMessageSubscriptionState;
  private final TransientPendingSubscriptionState transientProcessMessageSubscriptionState;
  private final ControllableStreamClock clock;
  private final SecurityConfiguration securityConfig;
  private final MeterRegistry meterRegistry;

  public TypedRecordProcessorContextImpl(
      final RecordProcessorContext context,
      final Writers writers,
      final EngineConfiguration config,
      final SecurityConfiguration securityConfig) {
    partitionId = context.getPartitionId();
    scheduleService = context.getScheduleService();
    zeebeDb = context.getZeebeDb();
    transientMessageSubscriptionState = new TransientPendingSubscriptionState();
    transientProcessMessageSubscriptionState = new TransientPendingSubscriptionState();
    clock = Objects.requireNonNull(context.getClock());
    processingState =
        new ProcessingDbState(
            partitionId,
            zeebeDb,
            context.getTransactionContext(),
            context.getKeyGenerator(),
            transientMessageSubscriptionState,
            transientProcessMessageSubscriptionState,
            config,
            clock);
    this.writers = writers;
    partitionCommandSender = context.getPartitionCommandSender();
    this.config = config;
    this.securityConfig = securityConfig;
    meterRegistry = context.getMeterRegistry();
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
  public Supplier<ScheduledTaskState> getScheduledTaskStateFactory() {
    return () ->
        new ScheduledTaskDbState(
            zeebeDb,
            zeebeDb.createContext(),
            partitionId,
            transientMessageSubscriptionState,
            transientProcessMessageSubscriptionState,
            clock,
            config);
  }

  @Override
  public EngineConfiguration getConfig() {
    return config;
  }

  @Override
  public SecurityConfiguration getSecurityConfig() {
    return securityConfig;
  }

  @Override
  public ControllableStreamClock getClock() {
    return clock;
  }

  @Override
  public TransientPendingSubscriptionState getTransientProcessMessageSubscriptionState() {
    return transientProcessMessageSubscriptionState;
  }

  @Override
  public MeterRegistry getMeterRegistry() {
    return meterRegistry;
  }
}
