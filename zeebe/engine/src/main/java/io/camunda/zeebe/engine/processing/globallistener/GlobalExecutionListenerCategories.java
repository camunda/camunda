/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.globallistener;

import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.GlobalListenerRecordValue;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Maps category names to sets of {@link BpmnElementType} values for global execution listeners.
 *
 * <p>Categories group related element types:
 *
 * <ul>
 *   <li>{@code tasks} — SERVICE_TASK, RECEIVE_TASK, USER_TASK, MANUAL_TASK, TASK, CALL_ACTIVITY,
 *       BUSINESS_RULE_TASK, SCRIPT_TASK, SEND_TASK
 *   <li>{@code gateways} — EXCLUSIVE_GATEWAY, PARALLEL_GATEWAY, EVENT_BASED_GATEWAY,
 *       INCLUSIVE_GATEWAY
 *   <li>{@code events} — START_EVENT, INTERMEDIATE_CATCH_EVENT, INTERMEDIATE_THROW_EVENT,
 *       BOUNDARY_EVENT, END_EVENT
 *   <li>{@code containers} — SUB_PROCESS, EVENT_SUB_PROCESS, AD_HOC_SUB_PROCESS,
 *       AD_HOC_SUB_PROCESS_INNER_INSTANCE, MULTI_INSTANCE_BODY
 *   <li>{@code all} — all of the above categories plus PROCESS
 * </ul>
 *
 * <p>Note: PROCESS is not part of any named category; it is only addressable via {@code
 * elementTypes: ["process"]} or via the {@code all} category.
 */
public final class GlobalExecutionListenerCategories {

  public static final String CATEGORY_TASKS = "tasks";
  public static final String CATEGORY_GATEWAYS = "gateways";
  public static final String CATEGORY_EVENTS = "events";
  public static final String CATEGORY_CONTAINERS = "containers";
  public static final String CATEGORY_ALL = "all";

  public static final String EVENT_TYPE_ALL = "all";

  public static final Set<String> ALL_CATEGORIES =
      Set.of(CATEGORY_TASKS, CATEGORY_GATEWAYS, CATEGORY_EVENTS, CATEGORY_CONTAINERS, CATEGORY_ALL);

  private static final EnumSet<BpmnElementType> TASKS_TYPES =
      EnumSet.of(
          BpmnElementType.SERVICE_TASK,
          BpmnElementType.RECEIVE_TASK,
          BpmnElementType.USER_TASK,
          BpmnElementType.MANUAL_TASK,
          BpmnElementType.TASK,
          BpmnElementType.CALL_ACTIVITY,
          BpmnElementType.BUSINESS_RULE_TASK,
          BpmnElementType.SCRIPT_TASK,
          BpmnElementType.SEND_TASK);

  private static final EnumSet<BpmnElementType> GATEWAYS_TYPES =
      EnumSet.of(
          BpmnElementType.EXCLUSIVE_GATEWAY,
          BpmnElementType.PARALLEL_GATEWAY,
          BpmnElementType.EVENT_BASED_GATEWAY,
          BpmnElementType.INCLUSIVE_GATEWAY);

  private static final EnumSet<BpmnElementType> EVENTS_TYPES =
      EnumSet.of(
          BpmnElementType.START_EVENT,
          BpmnElementType.INTERMEDIATE_CATCH_EVENT,
          BpmnElementType.INTERMEDIATE_THROW_EVENT,
          BpmnElementType.BOUNDARY_EVENT,
          BpmnElementType.END_EVENT);

  private static final EnumSet<BpmnElementType> CONTAINERS_TYPES =
      EnumSet.of(
          BpmnElementType.SUB_PROCESS,
          BpmnElementType.EVENT_SUB_PROCESS,
          BpmnElementType.AD_HOC_SUB_PROCESS,
          BpmnElementType.AD_HOC_SUB_PROCESS_INNER_INSTANCE,
          BpmnElementType.MULTI_INSTANCE_BODY);

  private static final Map<String, EnumSet<BpmnElementType>> CATEGORY_MAP =
      Map.of(
          CATEGORY_TASKS, TASKS_TYPES,
          CATEGORY_GATEWAYS, GATEWAYS_TYPES,
          CATEGORY_EVENTS, EVENTS_TYPES,
          CATEGORY_CONTAINERS, CONTAINERS_TYPES);

  private static final EnumSet<BpmnElementType> ALL_TYPES;

  static {
    ALL_TYPES = EnumSet.noneOf(BpmnElementType.class);
    ALL_TYPES.addAll(TASKS_TYPES);
    ALL_TYPES.addAll(GATEWAYS_TYPES);
    ALL_TYPES.addAll(EVENTS_TYPES);
    ALL_TYPES.addAll(CONTAINERS_TYPES);
    ALL_TYPES.add(BpmnElementType.PROCESS);
  }

  /** All valid element type name strings that can appear in the elementTypes configuration. */
  public static final Set<String> VALID_ELEMENT_TYPE_NAMES =
      ALL_TYPES.stream()
          .flatMap(t -> t.getElementTypeName().stream())
          .collect(Collectors.toUnmodifiableSet());

  private GlobalExecutionListenerCategories() {}

  /**
   * Returns true if the given global execution listener matches the given BPMN element type based
   * on the listener's configured categories and/or elementTypes.
   */
  public static boolean matchesElementType(
      final GlobalListenerRecordValue listener, final BpmnElementType elementType) {
    return matchesByCategory(listener, elementType)
        || matchesByElementTypeName(listener, elementType);
  }

  private static boolean matchesByCategory(
      final GlobalListenerRecordValue listener, final BpmnElementType elementType) {
    final var categories = listener.getCategories();
    if (categories == null || categories.isEmpty()) {
      return false;
    }
    return categories.stream()
        .anyMatch(
            category -> {
              if (CATEGORY_ALL.equals(category)) {
                return ALL_TYPES.contains(elementType);
              }
              final var types = CATEGORY_MAP.get(category);
              return types != null && types.contains(elementType);
            });
  }

  private static boolean matchesByElementTypeName(
      final GlobalListenerRecordValue listener, final BpmnElementType elementType) {
    final var elementTypes = listener.getElementTypes();
    if (elementTypes == null || elementTypes.isEmpty()) {
      return false;
    }
    return elementType.getElementTypeName().map(elementTypes::contains).orElse(false);
  }

  /**
   * Returns true if the given global execution listener matches the given execution event type
   * (start or end).
   */
  public static boolean matchesEventType(
      final GlobalListenerRecordValue listener, final String eventType) {
    final var eventTypes = listener.getEventTypes();
    if (eventTypes == null || eventTypes.isEmpty()) {
      return false;
    }
    return eventTypes.contains(eventType) || eventTypes.contains(EVENT_TYPE_ALL);
  }

  /** Returns the set of BpmnElementTypes for a given category name, or null if unknown. */
  public static Set<BpmnElementType> getTypesForCategory(final String category) {
    if (CATEGORY_ALL.equals(category)) {
      return ALL_TYPES;
    }
    return CATEGORY_MAP.get(category);
  }

  /** Validates that a category name is recognized. */
  public static boolean isValidCategory(final String category) {
    return ALL_CATEGORIES.contains(category);
  }

  /** Validates that an element type name is recognized. */
  public static boolean isValidElementTypeName(final String elementTypeName) {
    return VALID_ELEMENT_TYPE_NAMES.contains(elementTypeName);
  }

  /**
   * Resolves all valid element type name strings from a combination of categories and explicit
   * elementTypes. Used for validation purposes.
   */
  public static Set<String> resolveElementTypeNames(
      final Iterable<String> categories, final Iterable<String> elementTypes) {
    return Stream.concat(streamFromCategories(categories), streamFromElementTypeNames(elementTypes))
        .collect(Collectors.toUnmodifiableSet());
  }

  private static Stream<String> streamFromCategories(final Iterable<String> categories) {
    final var builder = Stream.<String>builder();
    for (final var category : categories) {
      final var types = getTypesForCategory(category);
      if (types != null) {
        types.forEach(t -> t.getElementTypeName().ifPresent(builder));
      }
    }
    return builder.build();
  }

  private static Stream<String> streamFromElementTypeNames(final Iterable<String> elementTypes) {
    final var builder = Stream.<String>builder();
    for (final var name : elementTypes) {
      if (VALID_ELEMENT_TYPE_NAMES.contains(name)) {
        builder.add(name);
      }
    }
    return builder.build();
  }
}
