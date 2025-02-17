/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ScheduledTaskState;
import io.camunda.zeebe.engine.state.message.TransientPendingSubscriptionState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.stream.api.InterPartitionCommandSender;
import io.camunda.zeebe.stream.api.StreamClock.ControllableStreamClock;
import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.function.Supplier;

public interface TypedRecordProcessorContext {

  int getPartitionId();

  ProcessingScheduleService getScheduleService();

  MutableProcessingState getProcessingState();

  Writers getWriters();

  InterPartitionCommandSender getPartitionCommandSender();

  /** Returns a state factory, where each created state has a separate transaction context. */
  Supplier<ScheduledTaskState> getScheduledTaskStateFactory();

  EngineConfiguration getConfig();

  SecurityConfiguration getSecurityConfig();

  ControllableStreamClock getClock();

  TransientPendingSubscriptionState getTransientProcessMessageSubscriptionState();

  MeterRegistry getMeterRegistry();
}
