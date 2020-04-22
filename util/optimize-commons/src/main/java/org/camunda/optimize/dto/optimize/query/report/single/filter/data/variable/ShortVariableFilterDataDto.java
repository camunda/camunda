/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.data.OperatorMultipleValuesVariableFilterSubDataDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;

import java.util.List;

public class ShortVariableFilterDataDto extends OperatorMultipleValuesVariableFilterDataDto {
  protected ShortVariableFilterDataDto() {
    this(null, null, null);
  }

  public ShortVariableFilterDataDto(final String name, final String operator, final List<String> values) {
    super(name, VariableType.SHORT, new OperatorMultipleValuesVariableFilterSubDataDto(operator, values));
  }
}
