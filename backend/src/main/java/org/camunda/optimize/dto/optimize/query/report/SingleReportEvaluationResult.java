/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.report;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import org.camunda.optimize.dto.optimize.rest.pagination.PaginatedDataExportDto;
import org.camunda.optimize.dto.optimize.rest.pagination.PaginationScrollableDto;
import org.camunda.optimize.service.es.report.result.RawDataCommandResult;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Data
@EqualsAndHashCode(callSuper = true)
public class SingleReportEvaluationResult<T> extends ReportEvaluationResult {
  @NonNull
  private List<CommandEvaluationResult<T>> commandEvaluationResults;

  public SingleReportEvaluationResult(@NonNull final SingleReportDefinitionDto<?> reportDefinition,
                                      @NonNull final CommandEvaluationResult<T> commandEvaluationResult) {
    super(reportDefinition);
    this.commandEvaluationResults = Collections.singletonList(commandEvaluationResult);
  }

  public SingleReportEvaluationResult(@NonNull final ReportDefinitionDto<?> reportDefinition,
                                      @NonNull final List<CommandEvaluationResult<T>> commandEvaluationResults) {
    super(reportDefinition);
    this.commandEvaluationResults = commandEvaluationResults;
  }

  public CommandEvaluationResult<T> getFirstCommandResult() {
    return commandEvaluationResults.stream().findFirst().orElse(null);
  }

  @Override
  public List<String[]> getResultAsCsv(final Integer limit, final Integer offset, final ZoneId timezone) {
    return commandEvaluationResults.get(0).getResultAsCsv(limit, offset, timezone);
  }

  @Override
  public PaginatedDataExportDto getResult() {
    final CommandEvaluationResult<?> commandResult = getFirstCommandResult();
    PaginatedDataExportDto result = new PaginatedDataExportDto();
    result.setData(commandResult.getResult());
    if (commandResult instanceof RawDataCommandResult) {
      result.setTotalNumberOfRecords(commandResult.getInstanceCount());
      if (commandResult.getPagination() instanceof PaginationScrollableDto) {
        result.setSearchRequestId(((PaginationScrollableDto) commandResult.getPagination()).getScrollId());
      } else {
        result.setSearchRequestId(null);
      }
    } else {
      Object data = Optional.ofNullable(result.getData()).orElse(new ArrayList<>());
      int payloadSize = (data instanceof List ? ((List<?>) data).size() : 1);
      result.setTotalNumberOfRecords(payloadSize);
    }
    return result;
  }
}
