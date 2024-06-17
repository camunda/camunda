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
import java.util.Optional;

public class StringVariableFilterDataDto extends OperatorMultipleValuesVariableFilterDataDto {
  protected StringVariableFilterDataDto() {
    this(null, null, null);
  }

  public StringVariableFilterDataDto(
      final String name, final FilterOperator operator, final List<String> values) {
    super(name, VariableType.STRING, new OperatorMultipleValuesFilterDataDto(operator, values));
  }

  public boolean hasContainsOperation() {
    return Optional.ofNullable(this.data.getOperator())
        .map(FilterOperator::isContainsOperation)
        .orElse(false);
  }

  public boolean hasEqualsOperation() {
    return Optional.ofNullable(this.data.getOperator())
        .map(FilterOperator::isEqualsOperation)
        .orElse(false);
  }
}
