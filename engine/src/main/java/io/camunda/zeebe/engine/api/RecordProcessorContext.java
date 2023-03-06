/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.api;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.state.EventApplier;
import io.camunda.zeebe.engine.state.KeyGenerator;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import java.util.List;
import java.util.function.Function;

public interface RecordProcessorContext {

  int getPartitionId();

  ProcessingScheduleService getScheduleService();

  ZeebeDb getZeebeDb();

  TransactionContext getTransactionContext();

  Function<MutableProcessingState, EventApplier> getEventApplierFactory();

  List<StreamProcessorLifecycleAware> getLifecycleListeners();

  void addLifecycleListeners(final List<StreamProcessorLifecycleAware> lifecycleListeners);

  InterPartitionCommandSender getPartitionCommandSender();

  KeyGenerator getKeyGenerator();
}
