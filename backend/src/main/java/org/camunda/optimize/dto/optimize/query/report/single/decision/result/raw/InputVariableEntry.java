package org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw;

import com.google.common.base.Objects;

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
    return Objects.equal(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(super.hashCode(), value);
  }
}
