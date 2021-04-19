/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.state.appliers;

import io.zeebe.engine.processing.deployment.model.element.ExecutableCallActivity;
import io.zeebe.engine.state.TypedEventApplier;
import io.zeebe.engine.state.immutable.ProcessState;
import io.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.zeebe.engine.state.mutable.MutableEventScopeInstanceState;
import io.zeebe.engine.state.mutable.MutableVariableState;
import io.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;

/** Applies state changes for `ProcessInstance:Element_Completed` */
final class ProcessInstanceElementCompletedApplier
    implements TypedEventApplier<ProcessInstanceIntent, ProcessInstanceRecord> {

  private final MutableElementInstanceState elementInstanceState;
  private final MutableEventScopeInstanceState eventScopeInstanceState;
  private final MutableVariableState variableState;
  private final ProcessState processState;
  private final BufferedStartMessageEventStateApplier bufferedStartMessageEventStateApplier;

  public ProcessInstanceElementCompletedApplier(
      final MutableElementInstanceState elementInstanceState,
      final MutableEventScopeInstanceState eventScopeInstanceState,
      final MutableVariableState variableState,
      final ProcessState processState,
      final BufferedStartMessageEventStateApplier bufferedStartMessageEventStateApplier) {
    this.elementInstanceState = elementInstanceState;
    this.eventScopeInstanceState = eventScopeInstanceState;
    this.variableState = variableState;
    this.processState = processState;
    this.bufferedStartMessageEventStateApplier = bufferedStartMessageEventStateApplier;
  }

  @Override
  public void applyState(final long key, final ProcessInstanceRecord value) {

    final var parentElementInstanceKey = value.getParentElementInstanceKey();

    if (parentElementInstanceKey > 0 && value.getBpmnElementType() == BpmnElementType.PROCESS) {
      // called by call activity

      final var parentElementInstance = elementInstanceState.getInstance(parentElementInstanceKey);

      final var elementId = parentElementInstance.getValue().getElementIdBuffer();

      final var callActivity =
          processState.getFlowElement(
              parentElementInstance.getValue().getProcessDefinitionKey(),
              elementId,
              ExecutableCallActivity.class);

      if (callActivity.getOutputMappings().isPresent()
          || callActivity.isPropagateAllChildVariablesEnabled()) {
        final var variables = variableState.getVariablesAsDocument(key);
        variableState.setTemporaryVariables(parentElementInstanceKey, variables);
      }
    }

    bufferedStartMessageEventStateApplier.removeMessageLock(value);

    eventScopeInstanceState.deleteInstance(key);
    elementInstanceState.removeInstance(key);
    variableState.removeTemporaryVariables(key);
  }
}
