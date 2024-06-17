/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable;

import io.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.OperatorMultipleValuesFilterDataDto;
import io.camunda.optimize.dto.optimize.query.variable.VariableType;
import java.util.List;

public class DoubleVariableFilterDataDto extends OperatorMultipleValuesVariableFilterDataDto {
  protected DoubleVariableFilterDataDto() {
    this(null, null, null);
  }

  public DoubleVariableFilterDataDto(
      final String name, final FilterOperator operator, final List<String> values) {
    super(name, VariableType.DOUBLE, new OperatorMultipleValuesFilterDataDto(operator, values));
  }
}
