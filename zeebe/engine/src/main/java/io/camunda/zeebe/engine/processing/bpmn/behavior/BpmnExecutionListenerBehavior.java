/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import io.camunda.zeebe.el.impl.StaticExpression;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowNode;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutionListener;
import io.camunda.zeebe.engine.processing.deployment.model.element.JobWorkerProperties;
import io.camunda.zeebe.engine.state.globallistener.GlobalListenersState;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeExecutionListenerEventType;
import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerRecord;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Merges BPMN model-level execution listeners with cluster-scoped global execution listeners.
 *
 * <p>Global execution listeners are registered at cluster level via the Orchestration Cluster API
 * or configuration files. They are merged with per-element execution listeners at runtime: global
 * listeners with {@code afterNonGlobal=false} (default) run before model-level listeners; those
 * with {@code afterNonGlobal=true} run after. Among global listeners, {@code priority} determines
 * order (higher value = runs first).
 *
 * <p>Element scoping: a global execution listener applies to an element if the element's type
 * matches any entry in the listener's {@code elementTypes} list, or falls within any of the
 * listener's {@code categories}. An empty {@code elementTypes} and {@code categories} means the
 * listener applies to all elements.
 */
public class BpmnExecutionListenerBehavior {

  /**
   * Maps category names (used in the REST API and config) to the set of camelCase element type
   * names that belong to each category.
   */
  private static final Map<String, Set<String>> CATEGORY_ELEMENT_TYPES;

  static {
    CATEGORY_ELEMENT_TYPES = new HashMap<>();
    CATEGORY_ELEMENT_TYPES.put(
        "tasks",
        Set.of(
            "serviceTask",
            "userTask",
            "sendTask",
            "receiveTask",
            "businessRuleTask",
            "scriptTask",
            "callActivity"));
    CATEGORY_ELEMENT_TYPES.put(
        "gateways",
        Set.of("exclusiveGateway", "parallelGateway", "inclusiveGateway", "eventBasedGateway"));
    CATEGORY_ELEMENT_TYPES.put(
        "events",
        Set.of(
            "startEvent",
            "endEvent",
            "intermediateCatchEvent",
            "intermediateThrowEvent",
            "boundaryEvent"));
    CATEGORY_ELEMENT_TYPES.put(
        "containers", Set.of("subProcess", "eventSubProcess", "multiInstanceBody"));
  }

  private final GlobalListenersState globalListenersState;

  public BpmnExecutionListenerBehavior(final GlobalListenersState globalListenersState) {
    this.globalListenersState = globalListenersState;
  }

  /**
   * Returns the merged list of start execution listeners for the given element. Global listeners
   * with {@code afterNonGlobal=false} appear first, followed by model-level listeners, then global
   * listeners with {@code afterNonGlobal=true}.
   */
  public List<ExecutionListener> getStartExecutionListeners(final ExecutableFlowNode element) {
    return mergeExecutionListeners(
        element, ZeebeExecutionListenerEventType.start, element.getStartExecutionListeners());
  }

  /**
   * Returns the merged list of end execution listeners for the given element. Global listeners with
   * {@code afterNonGlobal=false} appear first, followed by model-level listeners, then global
   * listeners with {@code afterNonGlobal=true}.
   */
  public List<ExecutionListener> getEndExecutionListeners(final ExecutableFlowNode element) {
    return mergeExecutionListeners(
        element, ZeebeExecutionListenerEventType.end, element.getEndExecutionListeners());
  }

  private List<ExecutionListener> mergeExecutionListeners(
      final ExecutableFlowNode element,
      final ZeebeExecutionListenerEventType eventType,
      final List<ExecutionListener> modelListeners) {

    final var config = globalListenersState.getCurrentConfig();
    if (config == null || config.getExecutionListeners().isEmpty()) {
      return modelListeners;
    }

    final String elementTypeName = toApiElementTypeName(element.getElementType());

    final List<ExecutionListener> beforeNonGlobal = new ArrayList<>();
    final List<ExecutionListener> afterNonGlobal = new ArrayList<>();

    config.getExecutionListeners().stream()
        .filter(listener -> appliesToElement(listener, elementTypeName))
        .filter(
            listener ->
                listener.getEventTypes().contains(eventType.name())
                    || listener.getEventTypes().contains("all"))
        .forEach(
            listener -> {
              final ExecutionListener el = toExecutionListener(listener, eventType);
              if (listener.isAfterNonGlobal()) {
                afterNonGlobal.add(el);
              } else {
                beforeNonGlobal.add(el);
              }
            });

    if (beforeNonGlobal.isEmpty() && afterNonGlobal.isEmpty()) {
      return modelListeners;
    }

    final List<ExecutionListener> result =
        new ArrayList<>(beforeNonGlobal.size() + modelListeners.size() + afterNonGlobal.size());
    result.addAll(beforeNonGlobal);
    result.addAll(modelListeners);
    result.addAll(afterNonGlobal);
    return result;
  }

  /**
   * Returns {@code true} if the global listener should apply to the element with the given type
   * name.
   */
  private boolean appliesToElement(
      final GlobalListenerRecord listener, final String elementTypeName) {
    final boolean hasElementTypes = !listener.getElementTypes().isEmpty();
    final boolean hasCategories = !listener.getCategories().isEmpty();

    if (!hasElementTypes && !hasCategories) {
      // No scoping constraints — applies to all elements
      return true;
    }

    if (hasElementTypes
        && (listener.getElementTypes().contains("all")
            || listener.getElementTypes().contains(elementTypeName))) {
      return true;
    }

    if (hasCategories) {
      for (final String category : listener.getCategories()) {
        if ("all".equals(category)) {
          return true;
        }
        final Set<String> categoryTypes = CATEGORY_ELEMENT_TYPES.get(category);
        if (categoryTypes != null && categoryTypes.contains(elementTypeName)) {
          return true;
        }
      }
    }

    return false;
  }

  private static ExecutionListener toExecutionListener(
      final GlobalListenerRecord record, final ZeebeExecutionListenerEventType eventType) {
    final ExecutionListener listener = new ExecutionListener();
    listener.setEventType(eventType);

    final JobWorkerProperties jobProperties = new JobWorkerProperties();
    jobProperties.setType(new StaticExpression(record.getType()));
    jobProperties.setRetries(new StaticExpression(String.valueOf(record.getRetries())));
    listener.setJobWorkerProperties(jobProperties);
    return listener;
  }

  /**
   * Converts a {@link BpmnElementType} enum constant to the camelCase string used in the REST API
   * and configuration (e.g. {@code SERVICE_TASK} → {@code "serviceTask"}).
   */
  static String toApiElementTypeName(final BpmnElementType elementType) {
    final String name = elementType.name().toLowerCase(Locale.ROOT);
    final String[] parts = name.split("_");
    final StringBuilder result = new StringBuilder(parts[0]);
    for (int i = 1; i < parts.length; i++) {
      if (!parts[i].isEmpty()) {
        result.append(Character.toUpperCase(parts[i].charAt(0)));
        result.append(parts[i], 1, parts[i].length());
      }
    }
    return result.toString();
  }
}
