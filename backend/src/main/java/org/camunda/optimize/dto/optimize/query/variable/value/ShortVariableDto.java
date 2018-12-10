package org.camunda.optimize.dto.optimize.query.variable.value;

import static org.camunda.optimize.service.util.ProcessVariableHelper.SHORT_TYPE;

public class ShortVariableDto extends VariableInstanceDto<Short> {

  private Short value;

  public ShortVariableDto() {
    super();
    setType(SHORT_TYPE);
  }

  public Short getValue() {
    return value;
  }

  public void setValue(Short value) {
    this.value = value;
  }

}
