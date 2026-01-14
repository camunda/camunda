/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.dto.listview;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.exceptions.OperateRuntimeException;
import java.io.IOException;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SortValuesWrapper implements Serializable {

  private static final Logger LOGGER = LoggerFactory.getLogger(SortValuesWrapper.class);

  private static final Set<Class<?>> ALLOWED_SORTVALUE_TYPES = new HashSet<>();

  // These values were taken from org.elasticsearch.search.searchafter.SearchAfterBuilder.
  // Opensearch does not have a filter and just passes any type further on
  static {
    ALLOWED_SORTVALUE_TYPES.add(String.class);
    ALLOWED_SORTVALUE_TYPES.add(Long.class);
    ALLOWED_SORTVALUE_TYPES.add(Integer.class);
    ALLOWED_SORTVALUE_TYPES.add(Short.class);
    ALLOWED_SORTVALUE_TYPES.add(Byte.class);
    ALLOWED_SORTVALUE_TYPES.add(Double.class);
    ALLOWED_SORTVALUE_TYPES.add(Float.class);
    ALLOWED_SORTVALUE_TYPES.add(Boolean.class);
    ALLOWED_SORTVALUE_TYPES.add(BigInteger.class);
  }

  public String value;
  public Class valueType;

  public SortValuesWrapper() {}

  public SortValuesWrapper(final String value, final Class valueType) {
    this.value = value;
    this.valueType = valueType;
  }

  public static SortValuesWrapper[] createFrom(
      final Object[] sortValues, final ObjectMapper objectMapper) {
    if (sortValues == null) {
      return null;
    }
    try {
      final List<SortValuesWrapper> sortValuesWrappers = new LinkedList<>();

      for (final Object sv : sortValues) {
        // Log if we are serializing a value that can't be deserialized later, will allow us to
        // discover new types to put in the allowlist
        if (!ALLOWED_SORTVALUE_TYPES.contains(sv.getClass())) {
          LOGGER.warn(
              "Serializing a sort value type that is not in the deserialization allowed list: {}",
              sv.getClass());
        }
        sortValuesWrappers.add(
            new SortValuesWrapper(objectMapper.writeValueAsString(sv), sv.getClass()));
      }
      return sortValuesWrappers.toArray(new SortValuesWrapper[0]);

    } catch (final JsonProcessingException e) {
      LOGGER.warn("Unable to serialize sortValues. Error: " + e.getMessage(), e);
      throw new OperateRuntimeException(e);
    }
  }

  public static Object[] convertSortValues(
      final SortValuesWrapper[] sortValuesWrappers, final ObjectMapper objectMapper) {
    if (sortValuesWrappers == null) {
      return null;
    }
    final List<Object> sortValues = new LinkedList<>();

    for (final SortValuesWrapper svw : sortValuesWrappers) {
      final var classType = svw.valueType;
      // These values can come as input from potentially untrusted sources. Sort values
      // are only expected to be of certain types, ensure that we don't try to deserialize
      // a bad type (both to prevent deserialization exploits and to not pass unsupported
      // types to elasticsearch/opensearch)
      if (ALLOWED_SORTVALUE_TYPES.contains(classType)) {
        try {
          sortValues.add(objectMapper.readValue(svw.value.getBytes(), classType));
        } catch (final IOException e) {
          LOGGER.error("Unable to deserialize sortValues. Error: {}", e.getMessage());
          throw new OperateRuntimeException(e);
        }
      } else {
        LOGGER.error("Unable to deserialize sortValues. Type {} is not allowed ", classType);
        throw new OperateRuntimeException("Invalid sortValues type: " + classType);
      }
    }

    return sortValues.toArray();
  }

  public Object getValue() {
    return value;
  }

  public SortValuesWrapper setValue(final String value) {
    this.value = value;
    return this;
  }

  public Class getValueType() {
    return valueType;
  }

  public SortValuesWrapper setValueType(final Class valueType) {
    this.valueType = valueType;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(value, valueType);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final SortValuesWrapper that = (SortValuesWrapper) o;
    return Objects.equals(value, that.value) && Objects.equals(valueType, that.valueType);
  }
}
