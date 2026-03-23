/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import io.camunda.zeebe.el.impl.StaticExpression;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowNode;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutionListener;
import io.camunda.zeebe.engine.processing.deployment.model.element.JobWorkerProperties;
import io.camunda.zeebe.engine.state.globallistener.GlobalListenersState;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeExecutionListenerEventType;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.GlobalListenerRecordValue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Behavior for merging global execution listeners with BPMN-level execution listeners.
 *
 * <p>Global execution listeners are defined at the cluster level and apply to all matching elements
 * across all processes. They are merged with BPMN-level execution listeners defined on individual
 * flow nodes. The merge order is: global-before + BPMN-level + global-after, where placement is
 * controlled by the {@code afterNonGlobal} flag on each global listener.
 */
public final class BpmnExecutionListenerBehavior {

  private final GlobalListenersState globalListenersState;
  private final ElementInstanceState elementInstanceState;

  public BpmnExecutionListenerBehavior(
      final GlobalListenersState globalListenersState,
      final ElementInstanceState elementInstanceState) {
    this.globalListenersState = globalListenersState;
    this.elementInstanceState = elementInstanceState;
  }

  /**
   * Returns the merged list of start execution listeners for the given flow node, combining global
   * and BPMN-level listeners.
   */
  public List<ExecutionListener> getStartExecutionListeners(
      final ExecutableFlowNode node, final BpmnElementContext context) {
    return getMergedExecutionListeners(
        node.getStartExecutionListeners(),
        context.getBpmnElementType(),
        "start",
        context.getElementInstanceKey());
  }

  /**
   * Returns the merged list of end execution listeners for the given flow node, combining global
   * and BPMN-level listeners.
   */
  public List<ExecutionListener> getEndExecutionListeners(
      final ExecutableFlowNode node, final BpmnElementContext context) {
    return getMergedExecutionListeners(
        node.getEndExecutionListeners(),
        context.getBpmnElementType(),
        "end",
        context.getElementInstanceKey());
  }

  private List<ExecutionListener> getMergedExecutionListeners(
      final List<ExecutionListener> bpmnListeners,
      final BpmnElementType bpmnElementType,
      final String eventType,
      final long elementInstanceKey) {

    final var instance = elementInstanceState.getInstance(elementInstanceKey);
    if (instance == null) {
      return bpmnListeners;
    }

    final long configKey = instance.getExecutionListenersConfigKey();
    if (configKey < 0) {
      return bpmnListeners;
    }

    final var pinnedConfig = globalListenersState.getVersionedConfig(configKey);
    if (pinnedConfig == null) {
      return bpmnListeners;
    }

    final var globalListeners = pinnedConfig.getExecutionListeners();
    if (globalListeners.isEmpty()) {
      return bpmnListeners;
    }

    // Filter global listeners that match this element type and event type,
    // then split into before/after non-global
    final List<ExecutionListener> beforeNonGlobal = new ArrayList<>();
    final List<ExecutionListener> afterNonGlobal = new ArrayList<>();

    for (final var listener : globalListeners) {
      if (!matchesEventType(listener, eventType)) {
        continue;
      }
      if (!GlobalExecutionListenerMatcher.matchesElementType(
          bpmnElementType, listener.getElementTypes(), listener.getCategories())) {
        continue;
      }
      if (!GlobalExecutionListenerMatcher.supportsEventType(bpmnElementType, eventType)) {
        continue;
      }

      final var target = listener.isAfterNonGlobal() ? afterNonGlobal : beforeNonGlobal;
      target.add(toExecutionListenerModel(listener, eventType));
    }

    if (beforeNonGlobal.isEmpty() && afterNonGlobal.isEmpty()) {
      return bpmnListeners;
    }

    // Merge: global-before + BPMN-level + global-after
    final List<ExecutionListener> merged =
        new ArrayList<>(beforeNonGlobal.size() + bpmnListeners.size() + afterNonGlobal.size());
    merged.addAll(beforeNonGlobal);
    merged.addAll(bpmnListeners);
    merged.addAll(afterNonGlobal);
    return Collections.unmodifiableList(merged);
  }

  private boolean matchesEventType(
      final GlobalListenerRecordValue listener, final String eventType) {
    return listener.getEventTypes().contains(eventType);
  }

  private ExecutionListener toExecutionListenerModel(
      final GlobalListenerRecordValue listener, final String eventType) {
    final var executionListener = new ExecutionListener();
    executionListener.setEventType(ZeebeExecutionListenerEventType.valueOf(eventType));

    final var jobProperties = new JobWorkerProperties();
    jobProperties.setType(new StaticExpression(listener.getType()));
    jobProperties.setRetries(new StaticExpression(String.valueOf(listener.getRetries())));
    executionListener.setJobWorkerProperties(jobProperties);

    return executionListener;
  }
}
