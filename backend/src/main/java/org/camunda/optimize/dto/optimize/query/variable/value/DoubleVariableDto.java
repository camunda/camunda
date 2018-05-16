package org.camunda.optimize.dto.optimize.query.variable.value;

import static org.camunda.optimize.service.util.VariableHelper.DOUBLE_TYPE;

public class DoubleVariableDto extends VariableInstanceDto {

  private Double value;

  public DoubleVariableDto() {
    super();
    setType(DOUBLE_TYPE);
  }

  public Double getValue() {
    return value;
  }

  public void setValue(Double value) {
    this.value = value;
  }

}
