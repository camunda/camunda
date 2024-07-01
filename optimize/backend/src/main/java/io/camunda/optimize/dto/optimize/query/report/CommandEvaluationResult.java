/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.report;

import io.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.result.MeasureDto;
import io.camunda.optimize.dto.optimize.query.report.single.result.ResultType;
import io.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@AllArgsConstructor
@RequiredArgsConstructor
@NoArgsConstructor
@Data
public abstract class CommandEvaluationResult<T> {

  protected long instanceCount;
  protected long instanceCountWithoutFilters;
  @NonNull protected List<MeasureDto<T>> measures = new ArrayList<>();
  @NonNull protected ReportDataDto reportData;
  @NonNull protected PaginationDto pagination = new PaginationDto(null, null);

  protected CommandEvaluationResult(
      @NonNull final List<MeasureDto<T>> measures, @NonNull final ReportDataDto reportData) {
    this.measures = measures;
    this.reportData = reportData;
  }

  protected CommandEvaluationResult(
      final long instanceCount,
      final long instanceCountWithoutFilters,
      @NonNull final List<MeasureDto<T>> measures,
      @NonNull final ReportDataDto reportData) {
    this.instanceCount = instanceCount;
    this.instanceCountWithoutFilters = instanceCountWithoutFilters;
    this.measures = measures;
    this.reportData = reportData;
  }

  public <R extends ReportDataDto> R getReportDataAs(Class<R> reportDataType) {
    return reportDataType.cast(reportData);
  }

  public T getFirstMeasureData() {
    return this.measures.stream().findFirst().map(MeasureDto::getData).orElse(null);
  }

  public void addMeasure(final MeasureDto<T> measureDto) {
    this.measures.add(measureDto);
  }

  public abstract List<String[]> getResultAsCsv(
      final Integer limit, final Integer offset, final ZoneId timezone);

  public abstract ResultType getType();

  public T getResult() {
    return getFirstMeasureData(); // This will be elaborated once we support the export of other
    // types of reports
  }

  protected String getGroupByIdentifier(final SingleReportDataDto reportData) {
    if (reportData instanceof ProcessReportDataDto) {
      return ((ProcessReportDataDto) reportData).getGroupBy().toString();
    } else {
      return ((DecisionReportDataDto) reportData).getGroupBy().toString();
    }
  }

  protected String getViewIdentifier(final SingleReportDataDto reportData) {
    if (reportData instanceof ProcessReportDataDto) {
      return ((ProcessReportDataDto) reportData).getView().createCommandKey().replace("-", "_");
    } else {
      return ((DecisionReportDataDto) reportData).getView().createCommandKey().replace("-", "_");
    }
  }
}
