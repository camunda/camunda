package org.camunda.optimize.dto.optimize.query.report.single.decision.group.value;

import java.util.Objects;

public class DecisionGroupByVariableValueDto implements DecisionGroupByValueDto {

  protected String id;

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
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
    return Objects.equals(id, that.id);
  }
}
