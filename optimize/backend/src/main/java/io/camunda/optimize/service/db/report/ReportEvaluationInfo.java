/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report;

import io.camunda.optimize.dto.optimize.query.report.AdditionalProcessReportEvaluationFilterDto;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;
import io.camunda.optimize.service.report.ReportService;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;

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
  private Set<String> hiddenFlowNodeIds;

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
    return this.report;
  }

  public String getReportId() {
    return this.reportId;
  }

  public String getUserId() {
    return this.userId;
  }

  public AdditionalProcessReportEvaluationFilterDto getAdditionalFilters() {
    return this.additionalFilters;
  }

  public ZoneId getTimezone() {
    return this.timezone;
  }

  public boolean isCsvExport() {
    return this.isCsvExport;
  }

  public boolean isJsonExport() {
    return this.isJsonExport;
  }

  public boolean isSharedReport() {
    return this.isSharedReport;
  }

  public Set<String> getHiddenFlowNodeIds() {
    return this.hiddenFlowNodeIds;
  }

  protected void setReport(ReportDefinitionDto<?> report) {
    this.report = report;
  }

  protected void setReportId(String reportId) {
    this.reportId = reportId;
  }

  protected void setUserId(String userId) {
    this.userId = userId;
  }

  protected void setAdditionalFilters(
      AdditionalProcessReportEvaluationFilterDto additionalFilters) {
    this.additionalFilters = additionalFilters;
  }

  protected void setTimezone(ZoneId timezone) {
    this.timezone = timezone;
  }

  protected void setPagination(PaginationDto pagination) {
    this.pagination = pagination;
  }

  protected void setCsvExport(boolean isCsvExport) {
    this.isCsvExport = isCsvExport;
  }

  protected void setJsonExport(boolean isJsonExport) {
    this.isJsonExport = isJsonExport;
  }

  protected void setSharedReport(boolean isSharedReport) {
    this.isSharedReport = isSharedReport;
  }

  protected void setHiddenFlowNodeIds(Set<String> hiddenFlowNodeIds) {
    this.hiddenFlowNodeIds = hiddenFlowNodeIds;
  }

  public static class ReportEvaluationInfoBuilder {

    private final ReportEvaluationInfo reportEvaluationInfo;

    public ReportEvaluationInfoBuilder(ReportEvaluationInfo reportEvaluationInfo) {
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
