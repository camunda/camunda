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

public abstract class CommandEvaluationResult<T> {

  protected long instanceCount;
  protected long instanceCountWithoutFilters;
  protected List<MeasureDto<T>> measures = new ArrayList<>();
  protected ReportDataDto reportData;
  protected PaginationDto pagination = new PaginationDto(null, null);

  protected CommandEvaluationResult(
      final List<MeasureDto<T>> measures, final ReportDataDto reportData) {
    if (measures == null) {
      throw new IllegalArgumentException("measures cannot be null");
    }

    if (reportData == null) {
      throw new IllegalArgumentException("reportData cannot be null");
    }

    this.measures = measures;
    this.reportData = reportData;
  }

  protected CommandEvaluationResult(
      final long instanceCount,
      final long instanceCountWithoutFilters,
      final List<MeasureDto<T>> measures,
      final ReportDataDto reportData) {
    if (measures == null) {
      throw new IllegalArgumentException("measures cannot be null");
    }

    if (reportData == null) {
      throw new IllegalArgumentException("reportData cannot be null");
    }

    this.instanceCount = instanceCount;
    this.instanceCountWithoutFilters = instanceCountWithoutFilters;
    this.measures = measures;
    this.reportData = reportData;
  }

  public CommandEvaluationResult(final ReportDataDto reportData) {
    if (reportData == null) {
      throw new IllegalArgumentException("reportData cannot be null");
    }

    this.reportData = reportData;
  }

  public CommandEvaluationResult(
      final long instanceCount,
      final long instanceCountWithoutFilters,
      final List<MeasureDto<T>> measures,
      final ReportDataDto reportData,
      final PaginationDto pagination) {
    if (measures == null) {
      throw new IllegalArgumentException("measures cannot be null");
    }

    if (reportData == null) {
      throw new IllegalArgumentException("reportData cannot be null");
    }

    if (pagination == null) {
      throw new IllegalArgumentException("pagination cannot be null");
    }

    this.instanceCount = instanceCount;
    this.instanceCountWithoutFilters = instanceCountWithoutFilters;
    this.measures = measures;
    this.reportData = reportData;
    this.pagination = pagination;
  }

  public CommandEvaluationResult() {}

  public <R extends ReportDataDto> R getReportDataAs(final Class<R> reportDataType) {
    return reportDataType.cast(reportData);
  }

  public T getFirstMeasureData() {
    return measures.stream().findFirst().map(MeasureDto::getData).orElse(null);
  }

  public void addMeasure(final MeasureDto<T> measureDto) {
    measures.add(measureDto);
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

  public long getInstanceCount() {
    return instanceCount;
  }

  public void setInstanceCount(final long instanceCount) {
    this.instanceCount = instanceCount;
  }

  public long getInstanceCountWithoutFilters() {
    return instanceCountWithoutFilters;
  }

  public void setInstanceCountWithoutFilters(final long instanceCountWithoutFilters) {
    this.instanceCountWithoutFilters = instanceCountWithoutFilters;
  }

  public List<MeasureDto<T>> getMeasures() {
    return measures;
  }

  public void setMeasures(final List<MeasureDto<T>> measures) {
    if (measures == null) {
      throw new IllegalArgumentException("measures cannot be null");
    }

    this.measures = measures;
  }

  public ReportDataDto getReportData() {
    return reportData;
  }

  public void setReportData(final ReportDataDto reportData) {
    if (reportData == null) {
      throw new IllegalArgumentException("reportData cannot be null");
    }

    this.reportData = reportData;
  }

  public PaginationDto getPagination() {
    return pagination;
  }

  public void setPagination(final PaginationDto pagination) {
    if (pagination == null) {
      throw new IllegalArgumentException("pagination cannot be null");
    }

    this.pagination = pagination;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof CommandEvaluationResult;
  }

  @Override
  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public String toString() {
    return "CommandEvaluationResult(instanceCount="
        + getInstanceCount()
        + ", instanceCountWithoutFilters="
        + getInstanceCountWithoutFilters()
        + ", measures="
        + getMeasures()
        + ", reportData="
        + getReportData()
        + ", pagination="
        + getPagination()
        + ")";
  }
}
