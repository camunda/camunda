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

public class InputVariableEntry extends VariableEntry {
  @Getter @Setter private Object value;

  protected InputVariableEntry() {
  }

  public InputVariableEntry(final String id, final String name, final VariableType type, final Object value) {
    super(id, name, type);
    this.value = value;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof InputVariableEntry)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final InputVariableEntry that = (InputVariableEntry) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), value);
  }
}
