package org.camunda.optimize.dto.optimize.query.variable.value;

import static org.camunda.optimize.service.util.VariableHelper.STRING_TYPE;

public class StringVariableDto extends VariableInstanceDto {

  private String value;

  public StringVariableDto() {
    super();
    setType(STRING_TYPE);
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

}
