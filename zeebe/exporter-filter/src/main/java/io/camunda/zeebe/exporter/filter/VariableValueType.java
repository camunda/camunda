/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** Inferred variable value types (aligned with Optimize's JSON-node based mapping). */
public enum VariableValueType {
  BOOLEAN,
  NUMBER,
  STRING,
  OBJECT,
  NULL,
  UNKNOWN;

  /**
   * Infers a logical type from the JSON representation of the variable value, mirroring Optimize's
   * ZeebeVariableImportService#getVariableTypeFromJsonNode:
   *
   * <p>NUMBER -> NUMBER BOOLEAN -> BOOLEAN STRING -> STRING OBJECT -> OBJECT ARRAY -> OBJECT
   *
   * <p>If parsing fails, falls back to UNKNOWN. Null raw value -> NULL.
   */
  static VariableValueType infer(final ObjectMapper objectMapper, final String raw) {
    if (raw == null) {
      return NULL;
    }

    try {
      final var jsonNode = objectMapper.readTree(raw);
      return switch (jsonNode.getNodeType()) {
        case NUMBER -> NUMBER;
        case BOOLEAN -> BOOLEAN;
        case STRING -> STRING;
        case OBJECT, ARRAY -> OBJECT;
        case NULL -> NULL;
        default -> UNKNOWN;
      };
    } catch (final JsonProcessingException e) {
      return UNKNOWN;
    }
  }

  static Set<VariableValueType> buildAllowedSet(
      final Set<VariableValueType> inclusion, final Set<VariableValueType> exclusion) {
    final EnumSet<VariableValueType> allowed =
        inclusion.isEmpty() ? EnumSet.allOf(VariableValueType.class) : EnumSet.copyOf(inclusion);
    allowed.removeAll(exclusion);
    return Collections.unmodifiableSet(allowed);
  }
}
