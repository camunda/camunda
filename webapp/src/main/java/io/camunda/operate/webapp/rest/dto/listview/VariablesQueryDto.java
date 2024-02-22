/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.rest.dto.listview;

import java.util.Arrays;
import java.util.Objects;

public class VariablesQueryDto {

  private String name;

  @Deprecated private String value;

  private String[] values;

  public VariablesQueryDto() {}

  public VariablesQueryDto(String variableName, String variableValue) {
    this.name = variableName;
    this.value = variableValue;
  }

  public VariablesQueryDto(String variableName, String[] values) {
    this.name = variableName;
    this.values = values;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @Deprecated
  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  public String[] getValues() {
    return values;
  }

  public VariablesQueryDto setValues(String[] values) {
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
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    VariablesQueryDto that = (VariablesQueryDto) o;
    return Objects.equals(name, that.name)
        && Objects.equals(value, that.value)
        && Arrays.equals(values, that.values);
  }
}
