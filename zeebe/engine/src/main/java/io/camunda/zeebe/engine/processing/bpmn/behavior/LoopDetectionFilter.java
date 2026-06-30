/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableMultiInstanceBody;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import org.agrona.DirectBuffer;

/**
 * Single source of truth deciding whether an element activation participates in loop detection.
 *
 * <p>The activation check (performed by {@code BpmnStreamProcessor}) and the activation counter
 * increment (performed by {@code ProcessInstanceElementActivatingV4Applier}) MUST agree on which
 * activations are counted. If they drift, an element could be checked but never counted or counted
 * but never checked. Both callers therefore delegate to this predicate.
 *
 * <p>Multi-instance elements are filtered to avoid false positives:
 *
 * <ul>
 *   <li>Sequential MI bodies are skipped: they share their {@code elementId} counter with their
 *       sequential children (the element that actually accumulates the count).
 *   <li>Parallel MI children are skipped: they are activated in a single batch and checked via the
 *       projected child-batch size instead.
 * </ul>
 *
 * All other elements (including parallel MI bodies and sequential MI children) are counted.
 */
public final class LoopDetectionFilter {

  private LoopDetectionFilter() {}

  /**
   * @param processState used to resolve multi-instance loop characteristics
   * @param value the record of the activated element
   * @param flowScopeInstance the flow scope of the activated element, or {@code null} if the
   *     element has no flow scopes
   * @return {@code true} if this activation should be counted for loop detection
   */
  public static boolean shouldCount(
      final ProcessState processState,
      final ProcessInstanceRecord value,
      final ElementInstance flowScopeInstance) {
    final var processDefinitionKey = value.getProcessDefinitionKey();
    final var tenantId = value.getTenantId();

    if (value.getBpmnElementType() == BpmnElementType.MULTI_INSTANCE_BODY) {
      // Only count parallel MI bodies; sequential MI bodies share their elementId counter with
      // their sequential children (the element that actually accumulates the count).
      final var miBody =
          getMultiInstanceBody(
              processState, processDefinitionKey, tenantId, value.getElementIdBuffer());
      return miBody != null && !miBody.getLoopCharacteristics().isSequential();
    }

    final var flowScopeValue = flowScopeInstance == null ? null : flowScopeInstance.getValue();
    if (flowScopeValue != null
        && flowScopeValue.getBpmnElementType() == BpmnElementType.MULTI_INSTANCE_BODY) {
      // Skip parallel MI children (activated in a batch, checked via the projected batch size).
      // Count sequential MI children (activated one-by-one, so normal loop detection applies).
      final var miBody =
          getMultiInstanceBody(
              processState, processDefinitionKey, tenantId, flowScopeValue.getElementIdBuffer());
      return miBody != null && miBody.getLoopCharacteristics().isSequential();
    }
    return true;
  }

  private static ExecutableMultiInstanceBody getMultiInstanceBody(
      final ProcessState processState,
      final long processDefinitionKey,
      final String tenantId,
      final DirectBuffer elementId) {
    return processState.getFlowElement(
        processDefinitionKey, tenantId, elementId, ExecutableMultiInstanceBody.class);
  }
}
