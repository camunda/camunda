/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.schema;

import io.camunda.optimize.dto.optimize.importing.FlowNodeEventDto;

public class ExtendedFlowNodeEventDto extends FlowNodeEventDto {

  private String myNewForbiddenField = "soDangerous";

  public String getMyNewForbiddenField() {
    return myNewForbiddenField;
  }

  public void setMyNewForbiddenField(String myNewForbiddenField) {
    this.myNewForbiddenField = myNewForbiddenField;
  }
}
