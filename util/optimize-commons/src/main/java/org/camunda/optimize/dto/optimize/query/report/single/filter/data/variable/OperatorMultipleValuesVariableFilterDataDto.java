/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable;

import org.camunda.optimize.dto.optimize.query.report.single.filter.data.OperatorMultipleValuesFilterDataDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;

public class OperatorMultipleValuesVariableFilterDataDto extends
  VariableFilterDataDto<OperatorMultipleValuesFilterDataDto> {
  public OperatorMultipleValuesVariableFilterDataDto(final String name,
                                                     final VariableType type,
                                                     final OperatorMultipleValuesFilterDataDto data) {
    super(name, type, data);
  }
}
