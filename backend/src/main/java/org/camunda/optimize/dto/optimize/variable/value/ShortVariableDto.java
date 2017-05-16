package org.camunda.optimize.dto.optimize.variable.value;

import static org.camunda.optimize.service.util.VariableHelper.SHORT_TYPE;

public class ShortVariableDto extends VariableInstanceDto<Short> {

  private short value;

  public ShortVariableDto() {
    super();
    setType(SHORT_TYPE);
  }

  public Short getValue() {
    return value;
  }

  public void setValue(short value) {
    this.value = value;
  }

}
