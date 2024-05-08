/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.dto.listview;

import static io.camunda.operate.util.LambdaExceptionUtil.rethrowFunction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.exceptions.OperateRuntimeException;
import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SortValuesWrapper implements Serializable {

  private static final Logger LOGGER = LoggerFactory.getLogger(SortValuesWrapper.class);

  public String value;
  public Class valueType;

  public SortValuesWrapper() {}

  public SortValuesWrapper(String value, Class valueType) {
    this.value = value;
    this.valueType = valueType;
  }

  public static SortValuesWrapper[] createFrom(Object[] sortValues, ObjectMapper objectMapper) {
    if (sortValues == null) {
      return null;
    }
    try {
      return Arrays.stream(sortValues)
          .map(
              rethrowFunction(
                  value ->
                      new SortValuesWrapper(
                          objectMapper.writeValueAsString(value), value.getClass())))
          .toArray(SortValuesWrapper[]::new);
    } catch (JsonProcessingException e) {
      LOGGER.warn("Unable to serialize sortValues. Error: " + e.getMessage(), e);
      throw new OperateRuntimeException(e);
    }
  }

  public static Object[] convertSortValues(
      SortValuesWrapper[] sortValuesWrappers, ObjectMapper objectMapper) {
    if (sortValuesWrappers == null) {
      return null;
    }
    try {
      return Arrays.stream(sortValuesWrappers)
          .map(
              rethrowFunction(
                  value -> objectMapper.readValue(value.value.getBytes(), value.valueType)))
          .toArray(Object[]::new);
    } catch (IOException e) {
      LOGGER.warn("Unable to deserialize sortValues. Error: " + e.getMessage(), e);
      throw new OperateRuntimeException(e);
    }
  }

  public Object getValue() {
    return value;
  }

  public SortValuesWrapper setValue(String value) {
    this.value = value;
    return this;
  }

  public Class getValueType() {
    return valueType;
  }

  public SortValuesWrapper setValueType(Class valueType) {
    this.valueType = valueType;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(value, valueType);
  }

  @Override
  public boolean equals(Object o) {
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
