/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.globallistener.MutableGlobalListenersState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableEventScopeInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableMultiInstanceState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;

/**
 * Applies state changes for {@code ProcessInstance:Element_Terminated}.
 *
 * <p>V3 extends V2 by unpinning the global execution listener configuration that was pinned during
 * ELEMENT_ACTIVATING or ELEMENT_COMPLETING, and garbage collecting the config version if no more
 * elements reference it. The unpin must happen before the element instance is removed from state.
 */
final class ProcessInstanceElementTerminatedV3Applier
    implements TypedEventApplier<ProcessInstanceIntent, ProcessInstanceRecord> {

  private final MutableElementInstanceState elementInstanceState;
  private final MutableEventScopeInstanceState eventScopeInstanceState;
  private final MutableMultiInstanceState multiInstanceState;
  private final BufferedStartMessageEventStateApplier bufferedStartMessageEventStateApplier;
  private final MutableGlobalListenersState globalListenersState;

  public ProcessInstanceElementTerminatedV3Applier(
      final MutableElementInstanceState elementInstanceState,
      final MutableEventScopeInstanceState eventScopeInstanceState,
      final MutableMultiInstanceState multiInstanceState,
      final BufferedStartMessageEventStateApplier bufferedStartMessageEventStateApplier,
      final MutableGlobalListenersState globalListenersState) {
    this.elementInstanceState = elementInstanceState;
    this.eventScopeInstanceState = eventScopeInstanceState;
    this.multiInstanceState = multiInstanceState;
    this.bufferedStartMessageEventStateApplier = bufferedStartMessageEventStateApplier;
    this.globalListenersState = globalListenersState;
  }

  @Override
  public void applyState(final long key, final ProcessInstanceRecord value) {

    bufferedStartMessageEventStateApplier.removeMessageLock(value);

    if (value.getBpmnElementType() == BpmnElementType.MULTI_INSTANCE_BODY) {
      multiInstanceState.deleteInputCollection(key);
    }

    // Unpin global execution listener config BEFORE removing the element instance from state
    unpinGlobalExecutionListenersConfig(key);

    eventScopeInstanceState.deleteInstance(key);
    elementInstanceState.removeInstance(key);

    if (value.getBpmnElementType() == BpmnElementType.PROCESS) {
      elementInstanceState.removeRuntimeInstructions(key);
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

  private void unpinGlobalExecutionListenersConfig(final long elementInstanceKey) {
    final var instance = elementInstanceState.getInstance(elementInstanceKey);
    if (instance == null) {
      return;
    }
    final long pinnedConfigKey = instance.getExecutionListenersConfigKey();
    if (pinnedConfigKey < 0) {
      return;
    }
    globalListenersState.unpinConfiguration(pinnedConfigKey, elementInstanceKey);
    if (!globalListenersState.isConfigurationVersionPinned(pinnedConfigKey)) {
      globalListenersState.deleteConfigurationVersion(pinnedConfigKey);
    }
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
