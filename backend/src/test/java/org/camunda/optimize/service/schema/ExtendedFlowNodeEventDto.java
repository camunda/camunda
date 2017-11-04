package org.camunda.optimize.service.schema;

import org.camunda.optimize.dto.optimize.importing.FlowNodeEventDto;

public class ExtendedFlowNodeEventDto extends FlowNodeEventDto {

  private String myNewForbiddenField = "soDangerous";

  public String getMyNewForbiddenField() {
    return myNewForbiddenField;
  }

  public void setMyNewForbiddenField(String myNewForbiddenField) {
    this.myNewForbiddenField = myNewForbiddenField;
  }
}
