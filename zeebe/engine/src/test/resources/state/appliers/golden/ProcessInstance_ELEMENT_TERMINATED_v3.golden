/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableEventScopeInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableMultiInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableSuspensionState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;

/**
 * Applies state changes for `ProcessInstance:Element_Terminated`.
 *
 * <p>Identical to {@link ProcessInstanceElementTerminatedV2Applier}, plus: whenever a process
 * instance's own root element terminates — including a call activity's child process instance,
 * which can be suspended independently of its parent — clears any suspension marker and buffered
 * commands left behind if it was terminated while {@code SUSPENDED} or {@code RESUMING}. Otherwise
 * that state would remain forever, since only the {@code RESUMED} applier used to clear it. Unlike
 * the business-id index below, this is intentionally not restricted to instances without a parent
 * process instance: suspension is not a root-only concept.
 */
final class ProcessInstanceElementTerminatedV3Applier
    implements TypedEventApplier<ProcessInstanceIntent, ProcessInstanceRecord> {

  private final MutableElementInstanceState elementInstanceState;
  private final MutableEventScopeInstanceState eventScopeInstanceState;
  private final MutableMultiInstanceState multiInstanceState;
  private final BufferedStartMessageEventStateApplier bufferedStartMessageEventStateApplier;
  private final MutableSuspensionState suspensionState;

  public ProcessInstanceElementTerminatedV3Applier(
      final MutableElementInstanceState elementInstanceState,
      final MutableEventScopeInstanceState eventScopeInstanceState,
      final MutableMultiInstanceState multiInstanceState,
      final BufferedStartMessageEventStateApplier bufferedStartMessageEventStateApplier,
      final MutableSuspensionState suspensionState) {
    this.elementInstanceState = elementInstanceState;
    this.eventScopeInstanceState = eventScopeInstanceState;
    this.multiInstanceState = multiInstanceState;
    this.bufferedStartMessageEventStateApplier = bufferedStartMessageEventStateApplier;
    this.suspensionState = suspensionState;
  }

  @Override
  public void applyState(final long key, final ProcessInstanceRecord value) {

    bufferedStartMessageEventStateApplier.removeMessageLock(value);

    if (value.getBpmnElementType() == BpmnElementType.MULTI_INSTANCE_BODY) {
      multiInstanceState.deleteInputCollection(key);
    }

    eventScopeInstanceState.deleteInstance(key);
    elementInstanceState.removeInstance(key);

    if (value.getBpmnElementType() == BpmnElementType.PROCESS) {
      elementInstanceState.removeRuntimeInstructions(key);
      suspensionState.removeSuspensionState(key);
      suspensionState.clearBufferedCommands(key);
    }

    if (value.getBpmnElementType() == BpmnElementType.PROCESS
        && !value.hasParentProcessInstance()) {
      deleteBusinessIdIndex(value);
    }

    final var flowScopeInstance = elementInstanceState.getInstance(value.getFlowScopeKey());

    if (flowScopeInstance == null) {
      return;
    }

    final var flowScopeElementType = flowScopeInstance.getValue().getBpmnElementType();
    manageMultiInstance(flowScopeInstance, flowScopeElementType);
  }

  private void manageMultiInstance(
      final ElementInstance flowScopeInstance, final BpmnElementType flowScopeElementType) {
    if (flowScopeElementType == BpmnElementType.MULTI_INSTANCE_BODY) {
      // update the numberOfTerminatedInstances of the multi-instance body
      flowScopeInstance.incrementNumberOfTerminatedElementInstances();
      elementInstanceState.updateInstance(flowScopeInstance);
    }
  }

  private void deleteBusinessIdIndex(final ProcessInstanceRecord value) {
    final String businessId = value.getBusinessId();
    if (!businessId.isEmpty()) {
      elementInstanceState.deleteProcessInstanceKeyMappingByBusinessId(
          businessId, value.getBpmnProcessId(), value.getTenantId(), value.getProcessInstanceKey());
    }
  }
}
