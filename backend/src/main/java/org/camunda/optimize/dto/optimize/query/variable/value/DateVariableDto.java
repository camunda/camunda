/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.variable.value;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

import static org.camunda.optimize.service.util.ProcessVariableHelper.DATE_TYPE;

@Getter
@Setter
public class DateVariableDto extends VariableInstanceDto {

  private OffsetDateTime value;

  public DateVariableDto() {
    super();
    setType(DATE_TYPE);
  }
}
