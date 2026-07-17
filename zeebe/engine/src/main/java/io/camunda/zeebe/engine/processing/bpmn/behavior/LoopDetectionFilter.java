/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;

/**
 * Single source of truth deciding whether an element activation participates in loop detection.
 *
 * <p>The activation check (performed by {@code BpmnStreamProcessor}) and the activation counter
 * increment (performed by {@code ProcessInstanceElementActivatingV4Applier}) both delegate to
 * {@link #shouldCount} for the base decision. This guarantees the dangerous direction never
 * happens: an element that is checked is always counted, so the threshold is never evaluated
 * against a counter that was never incremented.
 *
 * <p>The check layers additional, config-based gates on top of {@link #shouldCount} (a per-type
 * limit of {@code 0}, or a disabled {@code MULTI_INSTANCE_BODY} for multi-instance children), so a
 * disabled element type may be counted but never checked. That direction is harmless: the counter
 * accumulates unused and is removed when the process instance ends.
 *
 * <p>Multi-instance bodies are the only elements excluded from counting: the body itself is a
 * container, and its children accumulate the count instead. This holds for both flavours:
 *
 * <ul>
 *   <li>Sequential MI children are activated one-by-one, so each activation increments the counter.
 *   <li>Parallel MI children are activated through the activation batch; each child activation
 *       increments the same counter, so the batch is counted exactly rather than projected on the
 *       body.
 * </ul>
 *
 * <p>Because a multi-instance body and its inner activity share the same {@code elementId},
 * counting the children accumulates on a single per-element counter across every body activation,
 * giving the exact number of child activations even when the body is re-activated in a loop.
 */
public final class LoopDetectionFilter {

  private LoopDetectionFilter() {}

  /**
   * @param value the record of the activated element
   * @return {@code true} if this activation should be counted for loop detection
   */
  public static boolean shouldCount(final ProcessInstanceRecord value) {
    // The multi-instance body itself is never counted; its children accumulate the activation count
    // (sequential children one-by-one, parallel children through the activation batch). All other
    // elements, including the MI children, are counted.
    return value.getBpmnElementType() != BpmnElementType.MULTI_INSTANCE_BODY;
  }

  /**
   * @param flowScopeInstance the flow scope of the activated element, or {@code null} if the
   *     element has no flow scope
   * @return {@code true} if the activated element is a child of a multi-instance body
   */
  public static boolean isMultiInstanceChild(final ElementInstance flowScopeInstance) {
    return flowScopeInstance != null
        && flowScopeInstance.getValue().getBpmnElementType() == BpmnElementType.MULTI_INSTANCE_BODY;
  }
}
