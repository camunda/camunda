/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.api;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.stream.api.StreamClock.ControllableStreamClock;
import io.camunda.zeebe.stream.api.scheduling.ProcessingScheduleService;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;

public interface RecordProcessorContext {

  int getPartitionId();

  ProcessingScheduleService getScheduleService();

  ZeebeDb getZeebeDb();

  TransactionContext getTransactionContext();

  List<StreamProcessorLifecycleAware> getLifecycleListeners();

  void addLifecycleListeners(final List<StreamProcessorLifecycleAware> lifecycleListeners);

  InterPartitionCommandSender getPartitionCommandSender();

  KeyGenerator getKeyGenerator();

  ControllableStreamClock getClock();

  MeterRegistry getMeterRegistry();
}
