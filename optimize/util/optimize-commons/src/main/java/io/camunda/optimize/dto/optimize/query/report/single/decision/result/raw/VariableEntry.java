/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.decision.result.raw;

import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import java.util.Objects;

public class VariableEntry {

  private String id;
  private String name;
  private VariableType type;

  protected VariableEntry() {}

  public VariableEntry(final String id, final String name, final VariableType type) {
    this.id = id;
    this.name = name;
    this.type = type;
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name, type);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof VariableEntry)) {
      return false;
    }
    final VariableEntry that = (VariableEntry) o;
    return Objects.equals(id, that.id)
        && Objects.equals(name, that.name)
        && Objects.equals(type, that.type);
  }

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public VariableType getType() {
    return type;
  }

  public void setType(final VariableType type) {
    this.type = type;
  }
}
