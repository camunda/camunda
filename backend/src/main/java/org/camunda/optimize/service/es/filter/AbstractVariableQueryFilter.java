/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.filter;

import org.apache.commons.collections4.CollectionUtils;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.OperatorMultipleValuesVariableFilterDataDto;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;

import static org.camunda.optimize.dto.optimize.query.report.FilterOperatorConstants.RELATIVE_OPERATORS;

public abstract class AbstractVariableQueryFilter {
  protected void validateMultipleValuesFilterDataDto(final OperatorMultipleValuesVariableFilterDataDto dto) {
    if (CollectionUtils.isEmpty(dto.getData().getValues())) {
      throw new OptimizeValidationException("Filter values are not allowed to be empty.");
    }

    if (RELATIVE_OPERATORS.contains(dto.getData().getOperator()) && dto.getData().getValues().contains(null)) {
      throw new OptimizeValidationException(
        "Filter values are not allowed to contain `null` if a relative operator is used."
      );
    }
  }

  protected Object retrieveValue(OperatorMultipleValuesVariableFilterDataDto dto) {
    final String value = dto.getData().getValues().get(0);
    if (value != null) {
      switch (dto.getType()) {
        case INTEGER:
          return Integer.parseInt(value);
        case LONG:
          return Long.parseLong(value);
        case SHORT:
          return Short.parseShort(value);
        case DOUBLE:
          return Double.parseDouble(value);
        case STRING:
        case DATE:
        case BOOLEAN:
        default:
          return value;
      }
    } else {
      return null;
    }
  }
}
