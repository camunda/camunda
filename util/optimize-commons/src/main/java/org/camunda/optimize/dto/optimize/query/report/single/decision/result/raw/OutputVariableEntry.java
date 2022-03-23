/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class OutputVariableEntry extends VariableEntry {
  @Getter @Setter private List<Object> values = new ArrayList<>();

  protected OutputVariableEntry() {
  }

  public OutputVariableEntry(final String id, final String name, final VariableType type, final Object value) {
    super(id, name, type);
    this.values.add(value);
  }

  public OutputVariableEntry(final String id, final String name, final VariableType type, final Object... values) {
    super(id, name, type);
    this.values.addAll(Arrays.asList(values));
  }

  public OutputVariableEntry(final String id, final String name, final VariableType type, final List<Object> values) {
    super(id, name, type);
    this.values.addAll(values);
  }

  @JsonIgnore
  public Object getFirstValue() {
    return this.values.stream().findFirst().orElse(null);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof OutputVariableEntry)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final OutputVariableEntry that = (OutputVariableEntry) o;
    return Objects.equals(values, that.values);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), values);
  }
}
