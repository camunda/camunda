package org.camunda.optimize.dto.optimize.query.report.single.decision.group.value;

import java.util.Objects;

public class DecisionGroupByVariableValueDto implements DecisionGroupByValueDto {

  protected String name;
  protected String type;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  @Override
  public boolean isCombinable(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof DecisionGroupByVariableValueDto)) {
      return false;
    }
    DecisionGroupByVariableValueDto that = (DecisionGroupByVariableValueDto) o;
    return Objects.equals(name, that.name) && Objects.equals(type, that.type);
  }
}
