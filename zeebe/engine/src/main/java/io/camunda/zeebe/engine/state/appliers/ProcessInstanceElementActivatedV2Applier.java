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
 * Applies state changes for {@code ProcessInstance:Element_Activated}.
 *
 * <p>V2 extends V1 by unpinning the global execution listener configuration that was pinned during
 * ELEMENT_ACTIVATING, and garbage collecting the config version if no more elements reference it.
 */
final class ProcessInstanceElementActivatedV2Applier
    implements TypedEventApplier<ProcessInstanceIntent, ProcessInstanceRecord> {

  private final MutableElementInstanceState elementInstanceState;
  private final MutableGlobalListenersState globalListenersState;

  public ProcessInstanceElementActivatedV2Applier(
      final MutableElementInstanceState elementInstanceState,
      final MutableGlobalListenersState globalListenersState) {
    this.elementInstanceState = elementInstanceState;
    this.globalListenersState = globalListenersState;
  }

  @Override
  public void applyState(final long key, final ProcessInstanceRecord value) {
    elementInstanceState.updateInstance(
        key,
        instance -> {
          instance.setState(ProcessInstanceIntent.ELEMENT_ACTIVATED);
          instance.resetExecutionListenerIndex();
          unpinGlobalExecutionListenersConfig(key, instance.getExecutionListenersConfigKey());
        });
  }

  private void unpinGlobalExecutionListenersConfig(
      final long elementInstanceKey, final long pinnedConfigKey) {
    if (pinnedConfigKey < 0) {
      return;
    }
    globalListenersState.unpinConfiguration(pinnedConfigKey, elementInstanceKey);
    if (!globalListenersState.isConfigurationVersionPinned(pinnedConfigKey)) {
      globalListenersState.deleteConfigurationVersion(pinnedConfigKey);
    }
  }
}
