/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report;

import io.camunda.optimize.dto.optimize.query.report.AdditionalProcessReportEvaluationFilterDto;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;
import io.camunda.optimize.service.report.ReportService;
import java.time.ZoneId;
import java.util.Optional;

public class ReportEvaluationInfo {

  private ReportDefinitionDto<?> report;
  private String reportId;

  private String userId;
  private AdditionalProcessReportEvaluationFilterDto additionalFilters =
      new AdditionalProcessReportEvaluationFilterDto();
  private ZoneId timezone = ZoneId.systemDefault();
  private PaginationDto pagination;
  private boolean isCsvExport;
  private boolean isJsonExport;
  private boolean isSharedReport;

  private ReportEvaluationInfo() {}

  public void postFetchSavedReport(final ReportService reportService) {
    if (reportId != null) {
      report = reportService.getReportDefinition(reportId);
    }
  }

  public void updateReportDefinitionXml(final String definitionXml) {
    if (report.getData() instanceof final ProcessReportDataDto reportData) {
      reportData.getConfiguration().setXml(definitionXml);
    }
  }

  public Optional<PaginationDto> getPagination() {
    return Optional.ofNullable(pagination);
  }

  protected void setPagination(final PaginationDto pagination) {
    this.pagination = pagination;
  }

  public static ReportEvaluationInfoBuilder builder(final ReportDefinitionDto<?> report) {
    final ReportEvaluationInfo reportEvaluationInfo = new ReportEvaluationInfo();
    reportEvaluationInfo.setReport(report);
    return new ReportEvaluationInfoBuilder(reportEvaluationInfo);
  }

  public static ReportEvaluationInfoBuilder builder(final String reportId) {
    final ReportEvaluationInfo reportEvaluationInfo = new ReportEvaluationInfo();
    reportEvaluationInfo.setReportId(reportId);
    return new ReportEvaluationInfoBuilder(reportEvaluationInfo);
  }

  public ReportDefinitionDto<?> getReport() {
    return report;
  }

  protected void setReport(final ReportDefinitionDto<?> report) {
    this.report = report;
  }

  public String getReportId() {
    return reportId;
  }

  protected void setReportId(final String reportId) {
    this.reportId = reportId;
  }

  public String getUserId() {
    return userId;
  }

  protected void setUserId(final String userId) {
    this.userId = userId;
  }

  public AdditionalProcessReportEvaluationFilterDto getAdditionalFilters() {
    return additionalFilters;
  }

  protected void setAdditionalFilters(
      final AdditionalProcessReportEvaluationFilterDto additionalFilters) {
    this.additionalFilters = additionalFilters;
  }

  public ZoneId getTimezone() {
    return timezone;
  }

  protected void setTimezone(final ZoneId timezone) {
    this.timezone = timezone;
  }

  public boolean isCsvExport() {
    return isCsvExport;
  }

  protected void setCsvExport(final boolean isCsvExport) {
    this.isCsvExport = isCsvExport;
  }

  public boolean isJsonExport() {
    return isJsonExport;
  }

  protected void setJsonExport(final boolean isJsonExport) {
    this.isJsonExport = isJsonExport;
  }

  public boolean isSharedReport() {
    return isSharedReport;
  }

  protected void setSharedReport(final boolean isSharedReport) {
    this.isSharedReport = isSharedReport;
  }

  public static class ReportEvaluationInfoBuilder {

    private final ReportEvaluationInfo reportEvaluationInfo;

    public ReportEvaluationInfoBuilder(final ReportEvaluationInfo reportEvaluationInfo) {
      this.reportEvaluationInfo = reportEvaluationInfo;
    }

    public ReportEvaluationInfoBuilder userId(final String userId) {
      reportEvaluationInfo.setUserId(userId);
      return this;
    }

    public ReportEvaluationInfoBuilder additionalFilters(
        final AdditionalProcessReportEvaluationFilterDto additionalFilters) {
      reportEvaluationInfo.setAdditionalFilters(additionalFilters);
      return this;
    }

    public ReportEvaluationInfoBuilder timezone(final ZoneId timezone) {
      reportEvaluationInfo.setTimezone(timezone);
      return this;
    }

    public ReportEvaluationInfoBuilder pagination(final PaginationDto paginationDto) {
      reportEvaluationInfo.setPagination(paginationDto);
      return this;
    }

    public ReportEvaluationInfoBuilder isCsvExport(final boolean isExport) {
      reportEvaluationInfo.setCsvExport(isExport);
      return this;
    }

    public ReportEvaluationInfoBuilder isJsonExport(final boolean isExport) {
      reportEvaluationInfo.setJsonExport(isExport);
      return this;
    }

    public ReportEvaluationInfoBuilder isSharedReport(final boolean isSharedReport) {
      reportEvaluationInfo.setSharedReport(isSharedReport);
      return this;
    }

    public ReportEvaluationInfo build() {
      return reportEvaluationInfo;
    }
  }
}
