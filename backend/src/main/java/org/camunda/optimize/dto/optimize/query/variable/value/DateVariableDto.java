package org.camunda.optimize.dto.optimize.query.variable.value;

import java.time.OffsetDateTime;

import static org.camunda.optimize.service.util.VariableHelper.DATE_TYPE;

public class DateVariableDto extends VariableInstanceDto {

  private OffsetDateTime value;

  public DateVariableDto() {
    super();
    setType(DATE_TYPE);
  }

  public OffsetDateTime getValue() {
    return value;
  }

  public void setValue(OffsetDateTime value) {
    this.value = value;
  }

}
