/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;

public class VariableInputDTO {

  @Schema(description = "The name of the variable.")
  private String name;

  @Schema(
      description =
          "The value of the variable. When specifying the variable value, it's crucial to maintain consistency with JSON values (serialization for the complex objects such as list) and ensure that strings remain appropriately formatted.")
  private String value;

  public VariableInputDTO(final String name, final String value) {
    this.name = name;
    this.value = value;
  }

  public VariableInputDTO() {}

  public String getName() {
    return name;
  }

  public VariableInputDTO setName(final String name) {
    this.name = name;
    return this;
  }

  public String getValue() {
    return value;
  }

  public VariableInputDTO setValue(final String value) {
    this.value = value;
    return this;
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, value);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final VariableInputDTO that = (VariableInputDTO) o;
    return Objects.equals(name, that.name) && Objects.equals(value, that.value);
  }
}
