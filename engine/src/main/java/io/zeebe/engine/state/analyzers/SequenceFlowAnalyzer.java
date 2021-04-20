/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.state.analyzers;

import io.zeebe.engine.processing.deployment.model.element.ExecutableFlowNode;
import io.zeebe.engine.processing.deployment.model.element.ExecutableSequenceFlow;
import io.zeebe.engine.state.immutable.ElementInstanceState;
import io.zeebe.engine.state.instance.IndexedRecord;
import io.zeebe.protocol.record.intent.ProcessInstanceIntent;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;

public final class SequenceFlowAnalyzer {

  private final ElementInstanceState elementInstanceState;

  public SequenceFlowAnalyzer(final ElementInstanceState elementInstanceState) {
    this.elementInstanceState = elementInstanceState;
  }

  /**
   * Uses deferred records to determine which incoming sequence flows have been taken. Note that a
   * sequence flow may have been taken more than once (represented by the list of deferred records).
   *
   * @param flowScopeKey flow scope key of the element whose taken incoming sequence flows we want
   *     to determine. The deferred records must be stored at this key's scope
   * @param targetElement The target element for the incoming sequence flows
   * @return a map consisting of key (element id of taken sequence flow) -> value: (list of deferred
   *     records, for every time the sequence flow was taken). The keyset size is equal to how many
   *     of the incoming sequence flows have already been taken to the given target.
   */
  public Map<DirectBuffer, List<IndexedRecord>> determineTakenIncomingFlows(
      final long flowScopeKey, final ExecutableFlowNode targetElement) {
    return elementInstanceState.getDeferredRecords(flowScopeKey).stream()
        .filter(record -> record.getState() == ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN)
        .filter(record -> isIncomingSequenceFlow(record, targetElement))
        .collect(Collectors.groupingBy(record -> record.getValue().getElementIdBuffer()));
  }

  // copied from BpmnStateTransitionBehavior
  private boolean isIncomingSequenceFlow(
      final IndexedRecord record, final ExecutableFlowNode targetElement) {
    final var elementId = record.getValue().getElementIdBuffer();

    for (final ExecutableSequenceFlow incomingSequenceFlow : targetElement.getIncoming()) {
      if (elementId.equals(incomingSequenceFlow.getId())) {
        return true;
      }
    }
    return false;
  }
}
