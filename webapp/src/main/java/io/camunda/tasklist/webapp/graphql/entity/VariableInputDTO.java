/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.graphql.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;

public class VariableInputDTO {

  @Schema(description = "The name of the variable.")
  private String name;

  @Schema(
      description =
          "The value of the variable. When specifying the variable value, it's crucial to maintain consistency with JSON values (serialization for the complex objects such as list) and ensure that strings remain appropriately formatted.")
  private String value;

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

  @Override
  public int hashCode() {
    return Objects.hash(name, value);
  }
}
