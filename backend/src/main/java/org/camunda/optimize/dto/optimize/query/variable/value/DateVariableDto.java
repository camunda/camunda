/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.variable.value;

import java.time.OffsetDateTime;

import static org.camunda.optimize.service.util.ProcessVariableHelper.DATE_TYPE;

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
