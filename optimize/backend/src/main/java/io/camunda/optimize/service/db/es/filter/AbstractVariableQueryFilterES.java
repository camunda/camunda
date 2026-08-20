/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.filter;

import static io.camunda.optimize.service.db.filter.util.OperatorMultipleValuesVariableFilterDataDtoUtil.validateMultipleValuesFilterDataDto;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.BooleanVariableFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.DateVariableFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.OperatorMultipleValuesVariableFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.StringVariableFilterDataDto;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.time.ZoneId;
import java.util.List;
import org.slf4j.Logger;

public abstract class AbstractVariableQueryFilterES {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(AbstractVariableQueryFilterES.class);

  protected abstract Query.Builder createContainsOneOfTheGivenStringsQueryBuilder(
      final StringVariableFilterDataDto dto);

  protected abstract Query.Builder createContainsOneOfTheGivenStringsQueryBuilder(
      final String variableName, final List<String> values);

  protected abstract Query.Builder createContainsGivenStringQuery(
      final String variableId, final String valueToContain);

  protected abstract Query.Builder createEqualsOneOrMoreValuesQueryBuilder(
      final OperatorMultipleValuesVariableFilterDataDto dto);

  protected abstract Query.Builder createBooleanQueryBuilder(
      final BooleanVariableFilterDataDto dto);

  protected abstract Query.Builder createNumericQueryBuilder(
      OperatorMultipleValuesVariableFilterDataDto dto);

  protected abstract Query.Builder createDateQueryBuilder(
      final DateVariableFilterDataDto dto, final ZoneId timezone);

  protected Query.Builder createStringQueryBuilder(final StringVariableFilterDataDto stringVarDto) {
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
      LOG.debug(message);
      throw new OptimizeRuntimeException(message);
    }
  }
}
