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
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;

/**
 * Applies state changes for {@code ProcessInstance:Element_Completing}.
 *
 * <p>V2 extends V1 by re-pinning the current global execution listener configuration for end
 * execution listeners. The config may have changed since the element was activated.
 */
final class ProcessInstanceElementCompletingV2Applier
    implements TypedEventApplier<ProcessInstanceIntent, ProcessInstanceRecord> {

  private final MutableElementInstanceState elementInstanceState;
  private final MutableGlobalListenersState globalListenersState;

  public ProcessInstanceElementCompletingV2Applier(
      final MutableElementInstanceState elementInstanceState,
      final MutableGlobalListenersState globalListenersState) {
    this.elementInstanceState = elementInstanceState;
    this.globalListenersState = globalListenersState;
  }

  @Override
  public void applyState(final long elementInstanceKey, final ProcessInstanceRecord value) {
    elementInstanceState.updateInstance(
        elementInstanceKey,
        instance -> instance.setState(ProcessInstanceIntent.ELEMENT_COMPLETING));
    pinGlobalExecutionListenersConfig(elementInstanceKey);
  }

  private void pinGlobalExecutionListenersConfig(final long elementInstanceKey) {
    final var currentConfig = globalListenersState.getCurrentConfig();
    if (currentConfig == null) {
      return;
    }
    final var currentConfigKey = currentConfig.getGlobalListenerBatchKey();
    if (!globalListenersState.isConfigurationVersionStored(currentConfigKey)) {
      globalListenersState.storeConfigurationVersion(currentConfig);
    }
    globalListenersState.pinConfiguration(currentConfigKey, elementInstanceKey);
    elementInstanceState.updateInstance(
        elementInstanceKey, instance -> instance.setExecutionListenersConfigKey(currentConfigKey));
  }
}
