/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceBusinessIdRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceBusinessIdIntent;

/**
 * Applies the late assignment of a Business ID to a running root process instance (see ADR 0006).
 *
 * <p>It updates the Business ID on the process-scope element-instance record so that artifacts
 * created afterwards snapshot it, and inserts the uniqueness index for the root instance so the
 * existing completion/termination cleanup removes it later. The processor guarantees this event is
 * only produced for a root process instance that currently has no Business ID.
 */
final class ProcessInstanceBusinessIdAssignedV1Applier
    implements TypedEventApplier<ProcessInstanceBusinessIdIntent, ProcessInstanceBusinessIdRecord> {

  private final MutableElementInstanceState elementInstanceState;

  public ProcessInstanceBusinessIdAssignedV1Applier(
      final MutableElementInstanceState elementInstanceState) {
    this.elementInstanceState = elementInstanceState;
  }

  @Override
  public void applyState(final long key, final ProcessInstanceBusinessIdRecord value) {
    final String businessId = value.getBusinessId();

    elementInstanceState.updateInstance(
        value.getProcessInstanceKey(), instance -> instance.getValue().setBusinessId(businessId));

    elementInstanceState.insertProcessInstanceKeyByBusinessId(
        businessId, value.getBpmnProcessId(), value.getTenantId(), value.getProcessInstanceKey());
  }
}
