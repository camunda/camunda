/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.util.SemanticVersion;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VariableTypeFilter implements ExporterRecordFilter, RecordVersionFilter {

  private static final Logger LOG = LoggerFactory.getLogger(VariableTypeFilter.class);

  private static final SemanticVersion MIN_BROKER_VERSION =
      new SemanticVersion(8, 9, 0, null, null);

  private final ObjectMapper objectMapper;

  private final Set<VariableValueType> allowedTypes;

  public VariableTypeFilter(
      final Set<VariableValueType> inclusion, final Set<VariableValueType> exclusion) {
    this(new ObjectMapper(), inclusion, exclusion);
  }

  public VariableTypeFilter(
      final ObjectMapper objectMapper,
      final Set<VariableValueType> inclusion,
      final Set<VariableValueType> exclusion) {
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");

    final EnumSet<VariableValueType> allowed;
    if (inclusion.isEmpty()) {
      allowed = EnumSet.allOf(VariableValueType.class);
      allowed.removeAll(exclusion);
    } else {
      allowed = EnumSet.copyOf(inclusion);
      allowed.removeAll(exclusion);
    }

    allowedTypes = Collections.unmodifiableSet(allowed);
  }

  @Override
  public boolean accept(final Record<?> record) {
    if (!(record.getValue() instanceof final VariableRecordValue variableRecordValue)) {
      return true;
    }
    final VariableValueType inferredType = inferValueType(variableRecordValue.getValue());

    return allowedTypes.contains(inferredType);
  }

  /**
   * Infers a logical type from the JSON representation of the variable value, mirroring Optimize's
   * ZeebeVariableImportService#getVariableTypeFromJsonNode:
   *
   * <p>NUMBER -> NUMBER BOOLEAN -> BOOLEAN STRING -> STRING OBJECT -> OBJECT ARRAY -> OBJECT
   *
   * <p>If parsing fails, falls back to UNKNOWN. Null raw value -> NULL.
   */
  private VariableValueType inferValueType(final String raw) {
    if (raw == null) {
      return VariableValueType.NULL;
    }

    try {
      final JsonNode jsonNode = objectMapper.readTree(raw);
      if (jsonNode == null) {
        return VariableValueType.UNKNOWN;
      }

      return switch (jsonNode.getNodeType()) {
        case NUMBER -> VariableValueType.NUMBER;
        case BOOLEAN -> VariableValueType.BOOLEAN;
        case STRING -> VariableValueType.STRING;
        case OBJECT, ARRAY -> VariableValueType.OBJECT;
        case NULL -> VariableValueType.NULL;
        default -> VariableValueType.UNKNOWN;
      };
    } catch (final JsonProcessingException e) {
      return VariableValueType.UNKNOWN;
    }
  }

  public static Set<VariableValueType> parseTypes(final List<String> rawValues) {
    if (rawValues == null || rawValues.isEmpty()) {
      return Collections.emptySet();
    }

    final EnumSet<VariableValueType> result = EnumSet.noneOf(VariableValueType.class);

    for (final String value : rawValues) {
      if (value == null) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("parseTypes: skipping null value");
        }
        continue;
      }

      final String upper = value.trim().toUpperCase(Locale.ROOT);
      if (upper.isEmpty()) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("parseTypes: skipping empty value after trim (original='{}')", value);
        }
        continue;
      }

      if (upper.equals(VariableValueType.UNKNOWN.name())) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("parseTypes: skipping explicit UNKNOWN type token (original='{}')", value);
        }
        continue;
      }

      try {
        final VariableValueType parsed = VariableValueType.valueOf(upper);
        result.add(parsed);
      } catch (final IllegalArgumentException ex) {
        if (LOG.isDebugEnabled()) {
          LOG.debug(
              "parseTypes: unable to parse variable value type token '{}', skipping", value, ex);
        }
      }
    }

    return result;
  }

  @Override
  public SemanticVersion minRecordBrokerVersion() {
    return MIN_BROKER_VERSION;
  }

  /** Inferred variable value types (aligned with Optimize's JSON-node based mapping). */
  public enum VariableValueType {
    BOOLEAN,
    NUMBER,
    STRING,
    OBJECT,
    NULL,
    UNKNOWN
  }
}
