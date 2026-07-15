/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.projection.applier;

import io.camunda.analytics.projection.SourceRecord;
import io.camunda.analytics.projection.derive.BusinessValue;
import io.camunda.analytics.state.mutable.MutableProjectionState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;

/**
 * Upserts an element row {@code {start, ACTIVE, isProcess}} on activation, recording its parent
 * (flow) scope for later variable resolution. A process activation materializes the activation-time
 * business value on the row (see {@link BusinessValue} — the end fact subtracts exactly this,
 * keeping the in-flight level balanced); a non-process activation additionally bumps the instance's
 * variant accumulator ({@code (processInstanceKey, elementId) -> count}), the order-insensitive
 * input of the end fact's variant signature.
 */
public final class ElementActivatedApplier implements EventApplier {

  private final MutableProjectionState state;

  public ElementActivatedApplier(final MutableProjectionState state) {
    this.state = state;
  }

  @Override
  public void apply(final SourceRecord source) {
    final ProcessInstanceRecord value = (ProcessInstanceRecord) source.record().getValue();
    final boolean isProcess = value.getBpmnElementType() == BpmnElementType.PROCESS;
    // Start-payload variables are already in the store at this point — the creation command
    // persists them before the process element activates.
    final Long businessValue =
        isProcess ? BusinessValue.read(state, source.record().getKey()) : null;
    state.activateElement(
        source.record().getKey(),
        source.record().getTimestamp(),
        isProcess,
        value.getFlowScopeKey(),
        businessValue);
    if (!isProcess) {
      state.countVariantElement(value.getProcessInstanceKey(), value.getElementIdBuffer());
    }
  }
}
