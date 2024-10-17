/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report;

import io.camunda.optimize.dto.optimize.rest.pagination.PaginatedDataExportDto;
import io.camunda.optimize.dto.optimize.rest.pagination.PaginationScrollableDto;
import io.camunda.optimize.service.db.es.report.result.RawDataCommandResult;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class SingleReportEvaluationResult<T> extends ReportEvaluationResult {

  private List<CommandEvaluationResult<T>> commandEvaluationResults;

  public SingleReportEvaluationResult(
      final SingleReportDefinitionDto<?> reportDefinition,
      final CommandEvaluationResult<T> commandEvaluationResult) {
    super(reportDefinition);
    if (reportDefinition == null) {
      throw new IllegalArgumentException("reportDefinition cannot be null");
    }
    if (commandEvaluationResult == null) {
      throw new IllegalArgumentException("commandEvaluationResult cannot be null");
    }

    commandEvaluationResults = Collections.singletonList(commandEvaluationResult);
  }

  public SingleReportEvaluationResult(
      final ReportDefinitionDto<?> reportDefinition,
      final List<CommandEvaluationResult<T>> commandEvaluationResults) {
    super(reportDefinition);
    if (reportDefinition == null) {
      throw new IllegalArgumentException("reportDefinition cannot be null");
    }
    if (commandEvaluationResults == null) {
      throw new IllegalArgumentException("commandEvaluationResult cannot be null");
    }

    this.commandEvaluationResults = commandEvaluationResults;
  }

  public CommandEvaluationResult<T> getFirstCommandResult() {
    return commandEvaluationResults.stream().findFirst().orElse(null);
  }

  @Override
  public List<String[]> getResultAsCsv(
      final Integer limit, final Integer offset, final ZoneId timezone) {
    return commandEvaluationResults.get(0).getResultAsCsv(limit, offset, timezone);
  }

  @Override
  public PaginatedDataExportDto getResult() {
    final CommandEvaluationResult<?> commandResult = getFirstCommandResult();
    final PaginatedDataExportDto result = new PaginatedDataExportDto();
    result.setData(commandResult.getResult());
    if (commandResult instanceof RawDataCommandResult) {
      result.setTotalNumberOfRecords(commandResult.getInstanceCount());
      if (commandResult.getPagination() instanceof PaginationScrollableDto) {
        result.setSearchRequestId(
            ((PaginationScrollableDto) commandResult.getPagination()).getScrollId());
      } else {
        result.setSearchRequestId(null);
      }
    } else {
      final Object data = Optional.ofNullable(result.getData()).orElse(new ArrayList<>());
      final int payloadSize = (data instanceof List ? ((List<?>) data).size() : 1);
      result.setTotalNumberOfRecords(payloadSize);
    }
    return result;
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof SingleReportEvaluationResult;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = super.hashCode();
    final Object $commandEvaluationResults = getCommandEvaluationResults();
    result =
        result * PRIME
            + ($commandEvaluationResults == null ? 43 : $commandEvaluationResults.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof SingleReportEvaluationResult)) {
      return false;
    }
    final SingleReportEvaluationResult<?> other = (SingleReportEvaluationResult<?>) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final Object this$commandEvaluationResults = getCommandEvaluationResults();
    final Object other$commandEvaluationResults = other.getCommandEvaluationResults();
    if (this$commandEvaluationResults == null
        ? other$commandEvaluationResults != null
        : !this$commandEvaluationResults.equals(other$commandEvaluationResults)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "SingleReportEvaluationResult(commandEvaluationResults="
        + getCommandEvaluationResults()
        + ")";
  }

  public List<CommandEvaluationResult<T>> getCommandEvaluationResults() {
    return commandEvaluationResults;
  }

  public void setCommandEvaluationResults(
      final List<CommandEvaluationResult<T>> commandEvaluationResults) {
    if (commandEvaluationResults == null) {
      throw new IllegalArgumentException("commandEvaluationResults cannot be null");
    }

    this.commandEvaluationResults = commandEvaluationResults;
  }
}
