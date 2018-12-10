package org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class OutputVariableEntry extends VariableEntry {
  private List<Object> values = new ArrayList<>();

  protected OutputVariableEntry() {
  }

  public OutputVariableEntry(final String id, final String name, final String type, final Object value) {
    super(id, name, type);
    this.values.add(value);
  }

  public OutputVariableEntry(final String id, final String name, final String type, final Object... values) {
    super(id, name, type);
    this.values.addAll(Arrays.asList(values));
  }

  public OutputVariableEntry(final String id, final String name, final String type, final List<Object> values) {
    super(id, name, type);
    this.values.addAll(values);
  }

  @JsonIgnore
  public Object getFirstValue() {
    return this.values.stream().findFirst().orElse(null);
  }

  public List<Object> getValues() {
    return values;
  }

  public void setValues(final List<Object> values) {
    this.values = values;
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
