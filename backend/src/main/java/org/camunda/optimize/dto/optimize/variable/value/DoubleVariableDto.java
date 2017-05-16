package org.camunda.optimize.dto.optimize.variable.value;

import static org.camunda.optimize.service.util.VariableHelper.DOUBLE_TYPE;

public class DoubleVariableDto extends VariableInstanceDto {

  private double value;

  public DoubleVariableDto() {
    super();
    setType(DOUBLE_TYPE);
  }

  public Double getValue() {
    return value;
  }

  public void setValue(double value) {
    this.value = value;
  }

}
