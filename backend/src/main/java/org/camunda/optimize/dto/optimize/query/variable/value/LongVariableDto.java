/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.variable.value;

import lombok.Getter;
import lombok.Setter;

import static org.camunda.optimize.service.util.ProcessVariableHelper.LONG_TYPE;

@Getter
@Setter
public class LongVariableDto extends VariableInstanceDto {

  private Long value;

  public LongVariableDto() {
    super();
    setType(LONG_TYPE);
  }
}
