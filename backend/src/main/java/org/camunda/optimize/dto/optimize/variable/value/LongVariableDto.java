package org.camunda.optimize.dto.optimize.variable.value;

import static org.camunda.optimize.service.util.VariableHelper.LONG_TYPE;

public class LongVariableDto extends VariableInstanceDto {

  private long value;

  public LongVariableDto() {
    super();
    setType(LONG_TYPE);
  }

  public Long getValue() {
    return value;
  }

  public void setValue(long value) {
    this.value = value;
  }

}
