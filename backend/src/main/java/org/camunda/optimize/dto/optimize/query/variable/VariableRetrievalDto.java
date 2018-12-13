package org.camunda.optimize.dto.optimize.query.variable;

import org.camunda.optimize.dto.optimize.query.report.VariableType;

public class VariableRetrievalDto {

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

}
