/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.filter.util;

import static io.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.RELATIVE_OPERATORS;

import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.OperatorMultipleValuesVariableFilterDataDto;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.apache.commons.collections4.CollectionUtils;

public class OperatorMultipleValuesVariableFilterDataDtoUtil {
  public static void validateMultipleValuesFilterDataDto(
      final OperatorMultipleValuesVariableFilterDataDto dto) {
    if (CollectionUtils.isEmpty(dto.getData().getValues())) {
      throw new OptimizeValidationException("Filter values are not allowed to be empty.");
    }

    if (RELATIVE_OPERATORS.contains(dto.getData().getOperator())
        && dto.getData().getValues().contains(null)) {
      throw new OptimizeValidationException(
          "Filter values are not allowed to contain `null` if a relative operator is used.");
    }
  }

  public static Object retrieveValue(final OperatorMultipleValuesVariableFilterDataDto dto) {
    final String value = dto.getData().getValues().get(0);
    try {
      if (value != null) {
        return switch (dto.getType()) {
          case INTEGER -> Integer.parseInt(value);
          case LONG -> Long.parseLong(value);
          case SHORT -> Short.parseShort(value);
          case DOUBLE -> Double.parseDouble(value);
          default -> value;
        };
      } else {
        return null;
      }
    } catch (final NumberFormatException exception) {
      throw new OptimizeRuntimeException("Error trying to parse value for filter: " + value);
    }
  }
}
