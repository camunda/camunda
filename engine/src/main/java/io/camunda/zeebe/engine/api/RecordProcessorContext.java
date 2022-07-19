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
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessorFactory;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.EventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableZeebeState;
import java.util.List;
import java.util.function.Function;

public interface RecordProcessorContext {

  int getPartitionId();

  ProcessingScheduleService getScheduleService();

  ZeebeDb getZeebeDb();

  TransactionContext getTransactionContext();

  @Deprecated // will be removed soon
  TypedStreamWriter getStreamWriterProxy();

  @Deprecated // will be removed soon
  TypedResponseWriter getTypedResponseWriter();

  Function<MutableZeebeState, EventApplier> getEventApplierFactory();

  TypedRecordProcessorFactory getTypedRecordProcessorFactory();

  @Deprecated // will most likely be moved into engine
  void setLifecycleListeners(List<StreamProcessorLifecycleAware> lifecycleListeners);

  void setStreamProcessorListener(StreamProcessorListener streamProcessorListener);

  @Deprecated // will most likely be moved into engine
  void setWriters(Writers writers);
}
