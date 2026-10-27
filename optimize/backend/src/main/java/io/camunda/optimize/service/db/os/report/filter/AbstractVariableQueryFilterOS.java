/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.filter;

import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.BooleanVariableFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.DateVariableFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.OperatorMultipleValuesVariableFilterDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.filter.data.variable.StringVariableFilterDataDto;
import io.camunda.optimize.service.db.filter.util.OperatorMultipleValuesVariableFilterDataDtoUtil;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.time.ZoneId;
import java.util.List;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.slf4j.Logger;

public abstract class AbstractVariableQueryFilterOS {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(AbstractVariableQueryFilterOS.class);

  protected abstract Query createContainsOneOfTheGivenStringsQuery(
      final StringVariableFilterDataDto dto);

  protected abstract Query createContainsOneOfTheGivenStringsQuery(
      final String variableName, final List<String> values);

  protected abstract Query createContainsGivenStringQuery(
      final String variableId, final String valueToContain);

  protected abstract Query createEqualsOneOrMoreValuesQuery(
      final OperatorMultipleValuesVariableFilterDataDto dto);

  protected abstract Query createBooleanQuery(final BooleanVariableFilterDataDto dto);

  protected abstract Query createNumericQuery(OperatorMultipleValuesVariableFilterDataDto dto);

  protected abstract Query createDateQuery(
      final DateVariableFilterDataDto dto, final ZoneId timezone);

  protected Query createStringQuery(final StringVariableFilterDataDto stringVarDto) {
    OperatorMultipleValuesVariableFilterDataDtoUtil.validateMultipleValuesFilterDataDto(
        stringVarDto);

    if (stringVarDto.hasContainsOperation()) {
      return createContainsOneOfTheGivenStringsQuery(stringVarDto);
    } else if (stringVarDto.hasEqualsOperation()) {
      return createEqualsOneOrMoreValuesQuery(stringVarDto);
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
