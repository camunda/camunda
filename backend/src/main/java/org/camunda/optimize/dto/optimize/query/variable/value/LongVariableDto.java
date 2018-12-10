package org.camunda.optimize.dto.optimize.query.variable.value;

import static org.camunda.optimize.service.util.ProcessVariableHelper.LONG_TYPE;

public class LongVariableDto extends VariableInstanceDto {

  private Long value;

  public LongVariableDto() {
    super();
    setType(LONG_TYPE);
  }

  public Long getValue() {
    return value;
  }

  public void setValue(Long value) {
    this.value = value;
  }

}
