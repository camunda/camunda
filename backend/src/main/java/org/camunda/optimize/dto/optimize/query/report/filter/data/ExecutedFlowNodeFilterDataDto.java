package org.camunda.optimize.dto.optimize.query.report.filter.data;

import java.util.List;

public class ExecutedFlowNodeFilterDataDto implements FilterDataDto {

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
