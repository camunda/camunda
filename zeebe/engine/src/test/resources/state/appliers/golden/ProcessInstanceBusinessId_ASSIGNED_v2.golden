/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableCaseState;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceBusinessIdRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceBusinessIdIntent;
import org.jspecify.annotations.NullMarked;

/**
 * Applies the late assignment of a Business ID to a running root process instance (see ADR 0006).
 *
 * <p>It does what {@link ProcessInstanceBusinessIdAssignedV1Applier} does: it updates the Business
 * ID on the process-scope element-instance record so that artifacts created afterwards snapshot it,
 * and inserts the uniqueness index for the root instance so the existing completion/termination
 * cleanup removes it later.
 *
 * <p>In addition, it updates the Business ID of the case of the process instance, if the instance
 * is the root instance of a case. V1 stays registered, as events written before the case existed
 * must keep producing the state they produced then.
 */
@NullMarked
final class ProcessInstanceBusinessIdAssignedV2Applier
    implements TypedEventApplier<ProcessInstanceBusinessIdIntent, ProcessInstanceBusinessIdRecord> {

  private final MutableElementInstanceState elementInstanceState;
  private final MutableCaseState caseState;

  public ProcessInstanceBusinessIdAssignedV2Applier(
      final MutableElementInstanceState elementInstanceState, final MutableCaseState caseState) {
    this.elementInstanceState = elementInstanceState;
    this.caseState = caseState;
  }

  @Override
  public void applyState(final long key, final ProcessInstanceBusinessIdRecord value) {
    final String businessId = value.getBusinessId();
    final long processInstanceKey = value.getProcessInstanceKey();

    elementInstanceState.updateInstance(
        processInstanceKey, instance -> instance.getValue().setBusinessId(businessId));

    elementInstanceState.insertProcessInstanceKeyByBusinessId(
        businessId, value.getBpmnProcessId(), value.getTenantId(), processInstanceKey);

    if (caseState.exists(processInstanceKey)) {
      caseState.updateBusinessId(processInstanceKey, businessId);
    }
  }
}
