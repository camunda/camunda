package org.camunda.optimize.dto.optimize.query.report.single.group.value;

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
}
