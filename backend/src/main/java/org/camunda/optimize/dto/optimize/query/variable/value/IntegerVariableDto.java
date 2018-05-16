package org.camunda.optimize.dto.optimize.query.variable.value;

import static org.camunda.optimize.service.util.VariableHelper.INTEGER_TYPE;

public class IntegerVariableDto extends VariableInstanceDto {

  private Integer value;

  public IntegerVariableDto() {
    super();
    setType(INTEGER_TYPE);
  }

  public Integer getValue() {
    return value;
  }

  public void setValue(Integer value) {
    this.value = value;
  }

}
