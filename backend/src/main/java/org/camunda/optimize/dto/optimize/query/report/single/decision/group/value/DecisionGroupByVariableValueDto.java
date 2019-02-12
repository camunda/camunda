package org.camunda.optimize.dto.optimize.query.report.single.decision.group.value;

import java.util.Objects;
import java.util.Optional;

public class DecisionGroupByVariableValueDto implements DecisionGroupByValueDto {

  protected String id;
  protected String name;

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public Optional<String> getName() {
    return Optional.ofNullable(name);
  }

  public void setName(final String name) {
    this.name = name;
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
