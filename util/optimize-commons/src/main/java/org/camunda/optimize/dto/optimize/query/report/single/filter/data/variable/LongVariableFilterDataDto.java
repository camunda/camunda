/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.OperatorMultipleValuesFilterDataDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;

import java.util.List;

public class LongVariableFilterDataDto extends OperatorMultipleValuesVariableFilterDataDto {
  protected LongVariableFilterDataDto() {
    this(null, null, null);
  }

  public LongVariableFilterDataDto(final String name,
                                   final FilterOperator operator,
                                   final List<String> values) {
    super(name, VariableType.LONG, new OperatorMultipleValuesFilterDataDto(operator, values));
  }
}
