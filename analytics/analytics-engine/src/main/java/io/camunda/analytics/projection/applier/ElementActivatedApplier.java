/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.projection.applier;

import io.camunda.analytics.projection.SourceRecord;
import io.camunda.analytics.state.mutable.MutableProjectionState;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;

/**
 * Upserts an element row {@code {start, ACTIVE, isProcess}} on activation and indexes it under an
 * event-time straggler deadline, recording its parent (flow) scope for later variable resolution.
 */
public final class ElementActivatedApplier implements EventApplier {

  private final MutableProjectionState state;
  private final long slaMillis;

  public ElementActivatedApplier(final MutableProjectionState state, final long slaMillis) {
    this.state = state;
    this.slaMillis = slaMillis;
  }

  @Override
  public void apply(final SourceRecord source) {
    final ProcessInstanceRecordValue value =
        (ProcessInstanceRecordValue) source.record().getValue();
    final long elementInstanceKey = source.record().getKey();
    final long eventTimeMs = source.record().getTimestamp();
    state.activateElement(
        elementInstanceKey,
        eventTimeMs,
        value.getBpmnElementType() == BpmnElementType.PROCESS,
        value.getFlowScopeKey());
    state.putDeadline(eventTimeMs + slaMillis, elementInstanceKey);
  }
}
