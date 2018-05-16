package org.camunda.optimize.dto.optimize.query.variable.value;

import static org.camunda.optimize.service.util.VariableHelper.BOOLEAN_TYPE;

public class BooleanVariableDto extends VariableInstanceDto {

  private Boolean value;

  public BooleanVariableDto() {
    super();
    setType(BOOLEAN_TYPE);
  }

  public Boolean getValue() {
    return value;
  }

  public void setValue(Boolean value) {
    this.value = value;
  }

}
