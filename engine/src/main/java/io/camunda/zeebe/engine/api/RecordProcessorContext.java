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
import io.camunda.zeebe.engine.processing.streamprocessor.StreamProcessorListener;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.LegacyTypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.LegacyTypedStreamWriter;
import io.camunda.zeebe.engine.state.EventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableZeebeState;
import java.util.List;
import java.util.function.Function;

public interface RecordProcessorContext {

  int getPartitionId();

  ProcessingScheduleService getScheduleService();

  ZeebeDb getZeebeDb();

  TransactionContext getTransactionContext();

  LegacyTypedStreamWriter getStreamWriterProxy();

  LegacyTypedResponseWriter getTypedResponseWriter();

  Function<MutableZeebeState, EventApplier> getEventApplierFactory();

  List<StreamProcessorLifecycleAware> getLifecycleListeners();

  StreamProcessorListener getStreamProcessorListener();

  // only used for tests
  @Deprecated
  void setStreamProcessorListener(final StreamProcessorListener streamProcessorListener);

  void addLifecycleListeners(final List<StreamProcessorLifecycleAware> lifecycleListeners);
}
