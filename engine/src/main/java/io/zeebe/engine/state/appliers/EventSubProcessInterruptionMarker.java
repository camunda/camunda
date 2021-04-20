/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.state.appliers;

import io.zeebe.engine.processing.deployment.model.element.ExecutableCatchEvent;
import io.zeebe.engine.processing.deployment.model.element.ExecutableStartEvent;
import io.zeebe.engine.state.immutable.ProcessState;
import io.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.zeebe.protocol.record.value.BpmnElementType;
import org.agrona.DirectBuffer;

public class EventSubProcessInterruptionMarker {

  private final ProcessState processState;
  private final MutableElementInstanceState elementInstanceState;

  public EventSubProcessInterruptionMarker(
      final ProcessState processState, final MutableElementInstanceState elementInstanceState) {
    this.processState = processState;
    this.elementInstanceState = elementInstanceState;
  }

  /**
   * Marks the flow scope as interrupted, if the triggered element id, corresponds to an
   * interrupting event sub process.
   *
   * @param flowScopeElementInstanceKey the key of the flow scope, which should be marked as
   *     interrupted
   * @param processDefinitionKey the corresponding process definition key
   * @param elementId the id of the element which was triggered
   */
  public void markInstanceIfInterrupted(
      final long flowScopeElementInstanceKey,
      final long processDefinitionKey,
      final DirectBuffer elementId) {
    final var catchEvent =
        processState.getFlowElement(processDefinitionKey, elementId, ExecutableCatchEvent.class);
    if (!isRootStartEvent(flowScopeElementInstanceKey)
        && catchEvent.getFlowScope().getElementType() == BpmnElementType.EVENT_SUB_PROCESS
        && catchEvent instanceof ExecutableStartEvent
        && catchEvent.isInterrupting()) {
      final var executableStartEvent = (ExecutableStartEvent) catchEvent;

      // interrupting event sub process
      elementInstanceState.updateInstance(
          flowScopeElementInstanceKey,
          flowScopeElementInstance ->
              flowScopeElementInstance.setInterruptingElementId(
                  executableStartEvent.getEventSubProcess()));
    }
  }

  private boolean isRootStartEvent(final long elementInstanceKey) {
    return elementInstanceKey < 0;
  }
}
