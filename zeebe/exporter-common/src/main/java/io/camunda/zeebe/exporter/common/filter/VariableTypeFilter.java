/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class VariableTypeFilter implements ExporterRecordFilter, RecordVersionFilter {

  private static final String LIST_SEPARATOR = ";";
  private final ObjectMapper objectMapper;
  private final Set<VariableValueType> inclusion;
  private final Set<VariableValueType> exclusion;

  public VariableTypeFilter(
      final Set<VariableValueType> inclusion, final Set<VariableValueType> exclusion) {
    this(new ObjectMapper(), inclusion, exclusion);
  }

  public VariableTypeFilter(
      final ObjectMapper objectMapper,
      final Set<VariableValueType> inclusion,
      final Set<VariableValueType> exclusion) {
    this.objectMapper = objectMapper;
    this.inclusion = inclusion == null ? Collections.emptySet() : Set.copyOf(inclusion);
    this.exclusion = exclusion == null ? Collections.emptySet() : Set.copyOf(exclusion);
  }

  @Override
  public boolean accept(final Record<?> record) {
    if (!(record.getValue() instanceof final VariableRecordValue variableRecordValue)) {
      return true;
    }

    final VariableValueType inferredType = inferValueType(variableRecordValue.getValue());

    if (!inclusion.isEmpty() && !inclusion.contains(inferredType)) {
      return false;
    }

    return exclusion.isEmpty() || !exclusion.contains(inferredType);
  }

  /**
   * Infers a logical type from the JSON representation of the variable value, mirroring Optimize's
   * ZeebeVariableImportService#getVariableTypeFromJsonNode:
   *
   * <p>NUMBER -> DOUBLE BOOLEAN -> BOOLEAN STRING -> STRING OBJECT -> OBJECT ARRAY -> OBJECT
   *
   * <p>If parsing fails, falls back to STRING. Null raw value -> NULL.
   */
  private VariableValueType inferValueType(final String raw) {
    if (raw == null) {
      return VariableValueType.NULL;
    }

    try {
      final JsonNode jsonNode = objectMapper.readTree(raw);
      final JsonNodeType nodeType = jsonNode.getNodeType();

      return switch (nodeType) {
        case NUMBER -> VariableValueType.DOUBLE;
        case BOOLEAN -> VariableValueType.BOOLEAN;
        case STRING -> VariableValueType.STRING;
        case OBJECT, ARRAY -> VariableValueType.OBJECT;
        default -> VariableValueType.UNKNOWN;
      };
    } catch (final JsonProcessingException e) {
      // If it's not valid JSON, treat as plain string
      return VariableValueType.STRING;
    }
  }

  public static Set<VariableValueType> parseTypes(final List<String> rawValues) {
    if (rawValues == null || rawValues.isEmpty()) {
      return Collections.emptySet();
    }

    final EnumSet<VariableValueType> result = EnumSet.noneOf(VariableValueType.class);

    rawValues.forEach(
        s -> {
          final String upper = s.trim().toUpperCase(Locale.ROOT);
          try {
            if (upper.equals(VariableValueType.UNKNOWN.name())) {
              return; // Skip UNKNOWN type
            }
            result.add(VariableValueType.valueOf(upper));
          } catch (final IllegalArgumentException ignored) {
            // Unknown type token: ignore silently
          }
        });

    return result;
  }

  @Override
  public String minRecordVersion() {
    return "8.9.0";
  }

  /** Inferred variable value types (aligned with Optimize's JSON-node based mapping). */
  public enum VariableValueType {
    BOOLEAN,
    DOUBLE,
    STRING,
    OBJECT,
    NULL,
    UNKNOWN
  }
}
