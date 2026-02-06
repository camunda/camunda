/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.dto.listview;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Arrays;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class VariablesQueryDto {

  private String name;

  @Deprecated private String value;

  private String[] values;

  public VariablesQueryDto() {}

  public VariablesQueryDto(final String variableName, final String variableValue) {
    name = variableName;
    value = variableValue;
  }

  public VariablesQueryDto(final String variableName, final String[] values) {
    name = variableName;
    this.values = values;
  }

  public String getName() {
    return name;
  }

  public VariablesQueryDto setName(final String name) {
    this.name = name;
    return this;
  }

  @Deprecated
  public String getValue() {
    return value;
  }

  public VariablesQueryDto setValue(final String value) {
    this.value = value;
    return this;
  }

  public String[] getValues() {
    return values;
  }

  public VariablesQueryDto setValues(final String[] values) {
    this.values = values;
    return this;
  }

  @Override
  public int hashCode() {
    int result = Objects.hash(name, value);
    result = 31 * result + Arrays.hashCode(values);
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final VariablesQueryDto that = (VariablesQueryDto) o;
    return Objects.equals(name, that.name)
        && Objects.equals(value, that.value)
        && Arrays.equals(values, that.values);
  }
}
