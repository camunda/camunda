package org.camunda.optimize.service.schema;

import org.camunda.optimize.dto.optimize.importing.EventDto;

public class ExtendedEventDto extends EventDto {

  private String myNewForbiddenField = "soDangerous";

  public String getMyNewForbiddenField() {
    return myNewForbiddenField;
  }

  public void setMyNewForbiddenField(String myNewForbiddenField) {
    this.myNewForbiddenField = myNewForbiddenField;
  }
}
