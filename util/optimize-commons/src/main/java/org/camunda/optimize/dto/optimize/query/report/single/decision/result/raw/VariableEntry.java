/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw;

import lombok.Getter;
import lombok.Setter;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;

import java.util.Objects;

@Getter
@Setter
public class VariableEntry {
  private String id;
  private String name;
  private VariableType type;

  protected VariableEntry() {
  }

  public VariableEntry(final String id, final String name, final VariableType type) {
    this.id = id;
    this.name = name;
    this.type = type;
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
    return Objects.equals(id, that.id) &&
      Objects.equals(name, that.name) &&
      Objects.equals(type, that.type);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name, type);
  }
}
