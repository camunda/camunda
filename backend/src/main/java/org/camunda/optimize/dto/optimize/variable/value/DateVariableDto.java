package org.camunda.optimize.dto.optimize.variable.value;

import java.util.Date;

import static org.camunda.optimize.service.util.VariableHelper.DATE_TYPE;

public class DateVariableDto extends VariableInstanceDto {

  private Date value;

  public DateVariableDto() {
    super();
    setType(DATE_TYPE);
  }

  public Date getValue() {
    return value;
  }

  public void setValue(Date value) {
    this.value = value;
  }

}
