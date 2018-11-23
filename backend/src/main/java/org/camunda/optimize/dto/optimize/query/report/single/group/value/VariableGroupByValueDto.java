package org.camunda.optimize.dto.optimize.query.report.single.group.value;

import java.util.Objects;

public class VariableGroupByValueDto implements GroupByValueDto {

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
    if (!(o instanceof VariableGroupByValueDto)) {
      return false;
    }
    VariableGroupByValueDto that = (VariableGroupByValueDto) o;
    return Objects.equals(name, that.name) &&
      Objects.equals(type, that.type);
  }
}
