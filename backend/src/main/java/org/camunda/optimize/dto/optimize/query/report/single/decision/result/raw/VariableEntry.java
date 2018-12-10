package org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw;

import com.google.common.base.Objects;

public class VariableEntry {
  private String id;
  private String name;
  private String type;

  protected VariableEntry() {
  }

  public VariableEntry(final String id, final String name, final String type) {
    this.id = id;
    this.name = name;
    this.type = type;
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

  public String getType() {
    return type;
  }

  public void setType(final String type) {
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
    return Objects.equal(id, that.id) &&
      Objects.equal(name, that.name) &&
      Objects.equal(type, that.type);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id, name, type);
  }
}
