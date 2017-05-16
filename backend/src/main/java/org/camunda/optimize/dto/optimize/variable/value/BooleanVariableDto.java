package org.camunda.optimize.dto.optimize.variable.value;

import static org.camunda.optimize.service.util.VariableHelper.BOOLEAN_TYPE;

public class BooleanVariableDto extends VariableInstanceDto {

  private boolean value;

  public BooleanVariableDto() {
    super();
    setType(BOOLEAN_TYPE);
  }

  public Boolean getValue() {
    return value;
  }

  public void setValue(boolean value) {
    this.value = value;
  }

}
