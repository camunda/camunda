/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.variable.value;

import static org.camunda.optimize.service.util.ProcessVariableHelper.DOUBLE_TYPE;

public class DoubleVariableDto extends VariableInstanceDto {

  private Double value;

  public DoubleVariableDto() {
    super();
    setType(DOUBLE_TYPE);
  }

  public Double getValue() {
    return value;
  }

  public void setValue(Double value) {
    this.value = value;
  }

}
