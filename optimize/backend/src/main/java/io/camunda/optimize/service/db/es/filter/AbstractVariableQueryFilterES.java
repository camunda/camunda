/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.filter;

import static io.camunda.optimize.service.db.filter.util.OperatorMultipleValuesVariableFilterDataDtoUtil.validateMultipleValuesFilterDataDto;

import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.BooleanVariableFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.DateVariableFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.OperatorMultipleValuesVariableFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.StringVariableFilterDataDto;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.time.ZoneId;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;

@Slf4j
public abstract class AbstractVariableQueryFilterES {

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
}
