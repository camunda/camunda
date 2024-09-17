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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter(AccessLevel.PROTECTED)
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
