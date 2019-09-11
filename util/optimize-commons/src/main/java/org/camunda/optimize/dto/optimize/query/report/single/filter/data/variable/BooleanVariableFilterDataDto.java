/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.data.BooleanVariableFilterSubDataDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;

public class BooleanVariableFilterDataDto extends VariableFilterDataDto<BooleanVariableFilterSubDataDto> {

  protected BooleanVariableFilterDataDto() {
    this(null);
  }

  public BooleanVariableFilterDataDto(String value) {
    this.type = VariableType.BOOLEAN;
    BooleanVariableFilterSubDataDto dataDto = new BooleanVariableFilterSubDataDto();
    dataDto.setValue(value);
    setData(dataDto);
  }
}
