package org.camunda.optimize.dto.optimize.query.report.single.process.group.value;

import org.camunda.optimize.dto.optimize.query.variable.VariableType;

import java.util.Objects;

public class VariableGroupByValueDto implements ProcessGroupByValueDto {

  protected String name;
  protected VariableType type;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public VariableType getType() {
    return type;
  }

  public void setType(VariableType type) {
    this.type = type;
  }

  @Override
  public boolean isCombinable(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof VariableGroupByValueDto)) {
      return false;
    }
    VariableGroupByValueDto that = (VariableGroupByValueDto) o;
    return Objects.equals(name, that.name) &&
      Objects.equals(type, that.type);
  }
}
