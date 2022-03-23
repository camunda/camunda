/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
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
