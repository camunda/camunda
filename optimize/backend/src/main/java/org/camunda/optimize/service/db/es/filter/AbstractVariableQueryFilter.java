/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.es.filter;

import static org.camunda.optimize.dto.optimize.query.report.single.filter.data.FilterOperator.RELATIVE_OPERATORS;

import java.time.ZoneId;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.BooleanVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.DateVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.OperatorMultipleValuesVariableFilterDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.StringVariableFilterDataDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;

@Slf4j
public abstract class AbstractVariableQueryFilter {
  protected void validateMultipleValuesFilterDataDto(
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

  protected abstract QueryBuilder createContainsOneOfTheGivenStringsQueryBuilder(
      final StringVariableFilterDataDto dto);

  protected abstract BoolQueryBuilder createContainsOneOfTheGivenStringsQueryBuilder(
      final String variableName, final List<String> values);

  protected abstract QueryBuilder createContainsGivenStringQuery(
      final String variableId, final String valueToContain);

  protected abstract QueryBuilder createEqualsOneOrMoreValuesQueryBuilder(
      final OperatorMultipleValuesVariableFilterDataDto dto);

  protected abstract QueryBuilder createBooleanQueryBuilder(final BooleanVariableFilterDataDto dto);

  protected abstract QueryBuilder createNumericQueryBuilder(
      OperatorMultipleValuesVariableFilterDataDto dto);

  protected abstract QueryBuilder createDateQueryBuilder(
      final DateVariableFilterDataDto dto, final ZoneId timezone);

  protected QueryBuilder createStringQueryBuilder(final StringVariableFilterDataDto stringVarDto) {
    validateMultipleValuesFilterDataDto(stringVarDto);

    if (stringVarDto.hasContainsOperation()) {
      return createContainsOneOfTheGivenStringsQueryBuilder(stringVarDto);
    } else if (stringVarDto.hasEqualsOperation()) {
      return createEqualsOneOrMoreValuesQueryBuilder(stringVarDto);
    } else {
      final String message =
          String.format(
              "String variable operator [%s] is not supported!",
              stringVarDto.getData().getOperator().getId());
      log.debug(message);
      throw new OptimizeRuntimeException(message);
    }
  }

  protected Object retrieveValue(OperatorMultipleValuesVariableFilterDataDto dto) {
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
