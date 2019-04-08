/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.data.DateVariableFilterSubDataDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;

import java.time.OffsetDateTime;

public class DateVariableFilterDataDto extends VariableFilterDataDto<DateVariableFilterSubDataDto> {
  
  protected DateVariableFilterDataDto() {
    this(null, null);
  }

  public DateVariableFilterDataDto(OffsetDateTime start, OffsetDateTime end) {
    this.type = VariableType.DATE;
    DateVariableFilterSubDataDto dataDto = new DateVariableFilterSubDataDto();
    dataDto.setStart(start);
    dataDto.setEnd(end);
    setData(dataDto);
  }

}
