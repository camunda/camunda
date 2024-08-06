/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableProcessState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;

public class ProcessCreatedV1Applier implements TypedEventApplier<ProcessIntent, ProcessRecord> {

  private final MutableProcessState processState;

  public ProcessCreatedV1Applier(final MutableProcessingState state) {
    processState = state.getProcessState();
  }

  @Override
  public void applyState(final long processDefinitionKey, final ProcessRecord value) {
    processState.putProcess(processDefinitionKey, value);
  }
}
