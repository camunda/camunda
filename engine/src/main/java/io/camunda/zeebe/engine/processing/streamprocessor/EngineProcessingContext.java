/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.streamprocessor;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Builders;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.RecordsBuilder;
import io.camunda.zeebe.engine.state.EventApplier;
import io.camunda.zeebe.engine.state.ZeebeDbState;
import io.camunda.zeebe.engine.state.mutable.MutableZeebeState;

public interface EngineProcessingContext {

  MutableZeebeState getZeebeState();

  Builders getWriters();

  int getPartitionId();

  void transactionContext(TransactionContext transactionContext);

  void zeebeState(ZeebeDbState zeebeState);

  void initBuilders(RecordsBuilder recordsBuilder, EventApplier eventApplier);
}
