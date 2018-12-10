package org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw;


import java.util.Objects;

public class InputVariableEntry extends VariableEntry {
  private Object value;

  protected InputVariableEntry() {
  }

  public InputVariableEntry(final String id, final String name, final String type, final Object value) {
    super(id, name, type);
    this.value = value;
  }

  public Object getValue() {
    return value;
  }

  public void setValue(final Object value) {
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
