/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Maps global execution listener configuration element types and categories to {@link
 * BpmnElementType} values and validates supported event types per element type.
 */
public final class GlobalExecutionListenerMatcher {

  // Config element type string -> BpmnElementType mapping
  private static final Map<String, BpmnElementType> ELEMENT_TYPE_MAP = new HashMap<>();

  // BpmnElementType -> config element type string (reverse mapping)
  private static final Map<BpmnElementType, String> BPMN_TYPE_TO_CONFIG_NAME =
      new EnumMap<>(BpmnElementType.class);

  // Category -> set of config element type strings
  private static final Map<String, Set<String>> CATEGORY_EXPANSION = new HashMap<>();

  // Element types that support "start" event
  private static final Set<BpmnElementType> SUPPORTS_START =
      EnumSet.of(
          BpmnElementType.PROCESS,
          BpmnElementType.SUB_PROCESS,
          BpmnElementType.EVENT_SUB_PROCESS,
          BpmnElementType.SERVICE_TASK,
          BpmnElementType.USER_TASK,
          BpmnElementType.SEND_TASK,
          BpmnElementType.RECEIVE_TASK,
          BpmnElementType.SCRIPT_TASK,
          BpmnElementType.BUSINESS_RULE_TASK,
          BpmnElementType.CALL_ACTIVITY,
          BpmnElementType.MULTI_INSTANCE_BODY,
          BpmnElementType.EXCLUSIVE_GATEWAY,
          BpmnElementType.PARALLEL_GATEWAY,
          BpmnElementType.INCLUSIVE_GATEWAY,
          BpmnElementType.EVENT_BASED_GATEWAY,
          BpmnElementType.END_EVENT,
          BpmnElementType.INTERMEDIATE_CATCH_EVENT,
          BpmnElementType.INTERMEDIATE_THROW_EVENT);

  // Element types that support "end" event
  private static final Set<BpmnElementType> SUPPORTS_END =
      EnumSet.of(
          BpmnElementType.PROCESS,
          BpmnElementType.SUB_PROCESS,
          BpmnElementType.EVENT_SUB_PROCESS,
          BpmnElementType.SERVICE_TASK,
          BpmnElementType.USER_TASK,
          BpmnElementType.SEND_TASK,
          BpmnElementType.RECEIVE_TASK,
          BpmnElementType.SCRIPT_TASK,
          BpmnElementType.BUSINESS_RULE_TASK,
          BpmnElementType.CALL_ACTIVITY,
          BpmnElementType.MULTI_INSTANCE_BODY,
          BpmnElementType.START_EVENT,
          BpmnElementType.INTERMEDIATE_CATCH_EVENT,
          BpmnElementType.INTERMEDIATE_THROW_EVENT,
          BpmnElementType.BOUNDARY_EVENT);

  // Element types that support "cancel" event (only process)
  private static final Set<BpmnElementType> SUPPORTS_CANCEL = EnumSet.of(BpmnElementType.PROCESS);

  static {
    // Config string -> BpmnElementType
    ELEMENT_TYPE_MAP.put("process", BpmnElementType.PROCESS);
    ELEMENT_TYPE_MAP.put("subprocess", BpmnElementType.SUB_PROCESS);
    ELEMENT_TYPE_MAP.put("eventSubprocess", BpmnElementType.EVENT_SUB_PROCESS);
    ELEMENT_TYPE_MAP.put("serviceTask", BpmnElementType.SERVICE_TASK);
    ELEMENT_TYPE_MAP.put("userTask", BpmnElementType.USER_TASK);
    ELEMENT_TYPE_MAP.put("sendTask", BpmnElementType.SEND_TASK);
    ELEMENT_TYPE_MAP.put("receiveTask", BpmnElementType.RECEIVE_TASK);
    ELEMENT_TYPE_MAP.put("scriptTask", BpmnElementType.SCRIPT_TASK);
    ELEMENT_TYPE_MAP.put("businessRuleTask", BpmnElementType.BUSINESS_RULE_TASK);
    ELEMENT_TYPE_MAP.put("callActivity", BpmnElementType.CALL_ACTIVITY);
    ELEMENT_TYPE_MAP.put("multiInstanceBody", BpmnElementType.MULTI_INSTANCE_BODY);
    ELEMENT_TYPE_MAP.put("exclusiveGateway", BpmnElementType.EXCLUSIVE_GATEWAY);
    ELEMENT_TYPE_MAP.put("parallelGateway", BpmnElementType.PARALLEL_GATEWAY);
    ELEMENT_TYPE_MAP.put("inclusiveGateway", BpmnElementType.INCLUSIVE_GATEWAY);
    ELEMENT_TYPE_MAP.put("eventBasedGateway", BpmnElementType.EVENT_BASED_GATEWAY);
    ELEMENT_TYPE_MAP.put("startEvent", BpmnElementType.START_EVENT);
    ELEMENT_TYPE_MAP.put("endEvent", BpmnElementType.END_EVENT);
    ELEMENT_TYPE_MAP.put("intermediateCatchEvent", BpmnElementType.INTERMEDIATE_CATCH_EVENT);
    ELEMENT_TYPE_MAP.put("intermediateThrowEvent", BpmnElementType.INTERMEDIATE_THROW_EVENT);
    ELEMENT_TYPE_MAP.put("boundaryEvent", BpmnElementType.BOUNDARY_EVENT);

    // Reverse mapping
    ELEMENT_TYPE_MAP.forEach((name, type) -> BPMN_TYPE_TO_CONFIG_NAME.put(type, name));

    // Category expansions
    CATEGORY_EXPANSION.put(
        "tasks",
        Set.of(
            "serviceTask",
            "userTask",
            "sendTask",
            "receiveTask",
            "scriptTask",
            "businessRuleTask"));
    CATEGORY_EXPANSION.put(
        "gateways",
        Set.of("exclusiveGateway", "parallelGateway", "inclusiveGateway", "eventBasedGateway"));
    CATEGORY_EXPANSION.put(
        "events",
        Set.of(
            "startEvent",
            "endEvent",
            "intermediateCatchEvent",
            "intermediateThrowEvent",
            "boundaryEvent"));
  }

  private GlobalExecutionListenerMatcher() {}

  /**
   * Returns the config element type name for a given {@link BpmnElementType}, or {@code null} if
   * the type is not supported for global execution listeners.
   */
  public static String getConfigElementTypeName(final BpmnElementType bpmnElementType) {
    return BPMN_TYPE_TO_CONFIG_NAME.get(bpmnElementType);
  }

  /**
   * Returns whether a global execution listener matches the given BPMN element type based on its
   * configured {@code elementTypes} and {@code categories}.
   *
   * <p>If both lists are empty, the listener matches all element types (equivalent to {@code
   * categories: [all]}).
   */
  public static boolean matchesElementType(
      final BpmnElementType bpmnElementType,
      final List<String> elementTypes,
      final List<String> categories) {

    final String configName = BPMN_TYPE_TO_CONFIG_NAME.get(bpmnElementType);
    if (configName == null) {
      // Unmapped element type — not supported for global execution listeners
      return false;
    }

    final boolean hasElementTypes = elementTypes != null && !elementTypes.isEmpty();
    final boolean hasCategories = categories != null && !categories.isEmpty();

    // If both empty, matches all element types
    if (!hasElementTypes && !hasCategories) {
      return true;
    }

    // Check explicit element types
    if (hasElementTypes && elementTypes.contains(configName)) {
      return true;
    }

    // Check expanded categories
    if (hasCategories) {
      for (final String category : categories) {
        if ("all".equals(category)) {
          return true;
        }
        final Set<String> expanded = CATEGORY_EXPANSION.get(category);
        if (expanded != null && expanded.contains(configName)) {
          return true;
        }
      }
    }

    return false;
  }

  /**
   * Returns whether the given BPMN element type supports the specified event type.
   *
   * @param eventType the event type string: "start", "end", or "cancel"
   */
  public static boolean supportsEventType(
      final BpmnElementType bpmnElementType, final String eventType) {
    return switch (eventType) {
      case "start" -> SUPPORTS_START.contains(bpmnElementType);
      case "end" -> SUPPORTS_END.contains(bpmnElementType);
      case "cancel" -> SUPPORTS_CANCEL.contains(bpmnElementType);
      default -> false;
    };
  }

  /**
   * Resolves a config element type string to a {@link BpmnElementType}, or {@code null} if unknown.
   */
  public static BpmnElementType resolveElementType(final String configElementType) {
    return ELEMENT_TYPE_MAP.get(configElementType);
  }
}
