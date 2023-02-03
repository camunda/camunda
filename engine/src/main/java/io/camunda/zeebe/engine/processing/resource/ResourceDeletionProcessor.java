/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.resource;

import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.DecisionState;
import io.camunda.zeebe.protocol.impl.record.value.resource.ResourceDeletionRecord;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public class ResourceDeletionProcessor implements TypedRecordProcessor<ResourceDeletionRecord> {

  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;
  private final KeyGenerator keyGenerator;
  private final DecisionState decisionState;

  public ResourceDeletionProcessor(
      final Writers writers, final KeyGenerator keyGenerator, final DecisionState decisionState) {
    stateWriter = writers.state();
    responseWriter = writers.response();
    this.keyGenerator = keyGenerator;
    this.decisionState = decisionState;
  }

  @Override
  public void processRecord(final TypedRecord<ResourceDeletionRecord> command) {}
}
