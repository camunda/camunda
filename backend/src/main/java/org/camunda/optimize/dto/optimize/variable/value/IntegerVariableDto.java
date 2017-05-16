package org.camunda.optimize.dto.optimize.variable.value;

import static org.camunda.optimize.service.util.VariableHelper.INTEGER_TYPE;

public class IntegerVariableDto extends VariableInstanceDto {

  private int value;

  public IntegerVariableDto() {
    super();
    setType(INTEGER_TYPE);
  }

  public Integer getValue() {
    return value;
  }

  public void setValue(int value) {
    this.value = value;
  }

}
