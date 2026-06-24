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
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;

/** Applies state changes for `ProcessInstance:Element_Activated` */
final class ProcessInstanceElementActivatedApplier
    implements TypedEventApplier<ProcessInstanceIntent, ProcessInstanceRecord> {

  private final MutableElementInstanceState elementInstanceState;

  public ProcessInstanceElementActivatedApplier(
      final MutableElementInstanceState elementInstanceState) {
    this.elementInstanceState = elementInstanceState;
  }

  @Override
  public void applyState(final long key, final ProcessInstanceRecord value) {
    elementInstanceState.updateInstance(
        key,
        instance -> {
          instance.setState(ProcessInstanceIntent.ELEMENT_ACTIVATED);
          instance.resetExecutionListenerIndex();
        });
  }
}
