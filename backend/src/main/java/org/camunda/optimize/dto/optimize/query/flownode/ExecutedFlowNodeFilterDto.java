package org.camunda.optimize.dto.optimize.query.flownode;

import java.util.List;

public class ExecutedFlowNodeFilterDto {

  protected String operator;
  protected List<String> values;

  public String getOperator() {
    return operator;
  }

  public void setOperator(String operator) {
    this.operator = operator;
  }

  public List<String> getValues() {
    return values;
  }

  public void setValues(List<String> values) {
    this.values = values;
  }
}
