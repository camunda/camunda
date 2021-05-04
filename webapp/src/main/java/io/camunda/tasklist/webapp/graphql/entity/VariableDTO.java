/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.webapp.graphql.entity;

import io.camunda.tasklist.entities.TaskVariableEntity;
import java.util.Objects;

public class VariableDTO {

  private String name;
  private String value;

  public String getName() {
    return name;
  }

  public VariableDTO setName(final String name) {
    this.name = name;
    return this;
  }

  public String getValue() {
    return value;
  }

  public VariableDTO setValue(final String value) {
    this.value = value;
    return this;
  }

  public static VariableDTO createFrom(TaskVariableEntity variableEntity) {
    return new VariableDTO().setName(variableEntity.getName()).setValue(variableEntity.getValue());
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final VariableDTO that = (VariableDTO) o;
    return Objects.equals(name, that.name) && Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, value);
  }
}
