/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.rest.dto.listview;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.exceptions.OperateRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;

import static io.camunda.operate.util.LambdaExceptionUtil.rethrowFunction;

public class SortValuesWrapper implements Serializable {

  private static final Logger logger = LoggerFactory.getLogger(SortValuesWrapper.class);

  public String value;
  public Class valueType;

  public SortValuesWrapper() {
  }

  public SortValuesWrapper(String value, Class valueType) {
    this.value = value;
    this.valueType = valueType;
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

  public static SortValuesWrapper[] createFrom(Object[] sortValues, ObjectMapper objectMapper) {
    if (sortValues == null) {
      return null;
    }
    try {
      return Arrays.stream(sortValues).map(
              rethrowFunction(value -> new SortValuesWrapper(objectMapper.writeValueAsString(value), value.getClass())))
          .toArray(SortValuesWrapper[]::new);
    } catch (JsonProcessingException e) {
      logger.warn("Unable to serialize sortValues. Error: " + e.getMessage(), e);
      throw new OperateRuntimeException(e);
    }
  }

  public static Object[] convertSortValues(SortValuesWrapper[] sortValuesWrappers, ObjectMapper objectMapper) {
    if (sortValuesWrappers == null) {
      return null;
    }
    try {
      return Arrays.stream(sortValuesWrappers)
          .map(rethrowFunction(value -> objectMapper.readValue(value.value.getBytes(), value.valueType)))
          .toArray(Object[]::new);
    } catch (IOException e) {
      logger.warn("Unable to deserialize sortValues. Error: " + e.getMessage(), e);
      throw new OperateRuntimeException(e);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    SortValuesWrapper that = (SortValuesWrapper) o;
    return Objects.equals(value, that.value) && Objects.equals(valueType, that.valueType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value, valueType);
  }
}
