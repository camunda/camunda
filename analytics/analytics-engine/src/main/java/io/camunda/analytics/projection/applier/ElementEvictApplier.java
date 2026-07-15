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
 * Evicts a terminal element row after its completion fact has been derived (evict-after-emit):
 * drops the row and the variables scoped to that element instance. The process row's eviction —
 * once per instance, after its end fact was derived — also drops the instance's variant
 * accumulator, whose signature that fact already carries.
 */
public final class ElementEvictApplier implements EventApplier {

  private final MutableProjectionState state;

  public ElementEvictApplier(final MutableProjectionState state) {
    this.state = state;
  }

  @Override
  public void apply(final SourceRecord source) {
    final long elementInstanceKey = source.record().getKey();
    state.clearVariables(elementInstanceKey);
    state.evictElement(elementInstanceKey);
    final ProcessInstanceRecordValue value =
        (ProcessInstanceRecordValue) source.record().getValue();
    if (value.getBpmnElementType() == BpmnElementType.PROCESS) {
      state.clearVariantElements(value.getProcessInstanceKey());
    }
  }
}
