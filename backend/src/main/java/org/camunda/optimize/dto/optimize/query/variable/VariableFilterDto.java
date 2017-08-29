package org.camunda.optimize.dto.optimize.query.variable;

import java.util.List;

public class VariableFilterDto {

  protected String name;
  protected String operator;
  protected String type;
  protected List<String> values;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getOperator() {
    return operator;
  }

  public void setOperator(String operator) {
    this.operator = operator;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public List<String> getValues() {
    return values;
  }

  public void setValues(List<String> values) {
    this.values = values;
  }
}
