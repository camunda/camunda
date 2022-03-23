/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.camunda.optimize.dto.optimize.query.report.AdditionalProcessReportEvaluationFilterDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;
import org.camunda.optimize.service.es.reader.ReportReader;

import javax.ws.rs.NotFoundException;
import java.time.ZoneId;
import java.util.Optional;

@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ReportEvaluationInfo {

  private ReportDefinitionDto<?> report;
  private String reportId;

  private String userId;
  private AdditionalProcessReportEvaluationFilterDto additionalFilters =
    new AdditionalProcessReportEvaluationFilterDto();
  private ZoneId timezone = ZoneId.systemDefault();
  private PaginationDto pagination;
  private boolean isCsvExport;
  private boolean isSharedReport;
  private boolean isJsonExport;

  public void postFetchSavedReport(final ReportReader reportReader) {
    if (reportId != null) {
      report = reportReader.getReport(reportId)
        .orElseThrow(() -> new NotFoundException("Report with id [" + reportId + "] does not exist"));
    }
  }

  public Optional<PaginationDto> getPagination()
  {
    return Optional.ofNullable(pagination);
  }

  public static ReportEvaluationInfoBuilder builder(final ReportDefinitionDto<?> report) {
    ReportEvaluationInfo reportEvaluationInfo = new ReportEvaluationInfo();
    reportEvaluationInfo.setReport(report);
    return new ReportEvaluationInfoBuilder(reportEvaluationInfo);
  }

  public static ReportEvaluationInfoBuilder builder(final String reportId) {
    ReportEvaluationInfo reportEvaluationInfo = new ReportEvaluationInfo();
    reportEvaluationInfo.setReportId(reportId);
    return new ReportEvaluationInfoBuilder(reportEvaluationInfo);
  }

  @RequiredArgsConstructor
  public static class ReportEvaluationInfoBuilder {

    private final ReportEvaluationInfo reportEvaluationInfo;

    public ReportEvaluationInfoBuilder userId(final String userId) {
      this.reportEvaluationInfo.setUserId(userId);
      return this;
    }

    public ReportEvaluationInfoBuilder additionalFilters(final AdditionalProcessReportEvaluationFilterDto additionalFilters) {
      this.reportEvaluationInfo.setAdditionalFilters(additionalFilters);
      return this;
    }

    public ReportEvaluationInfoBuilder timezone(final ZoneId timezone) {
      this.reportEvaluationInfo.setTimezone(timezone);
      return this;
    }

    public ReportEvaluationInfoBuilder pagination(final PaginationDto paginationDto) {
      this.reportEvaluationInfo.setPagination(paginationDto);
      return this;
    }

    public ReportEvaluationInfoBuilder isCsvExport(final boolean isExport) {
      this.reportEvaluationInfo.setCsvExport(isExport);
      return this;
    }

    public ReportEvaluationInfoBuilder isJsonExport(final boolean isExport) {
      this.reportEvaluationInfo.setJsonExport(isExport);
      return this;
    }

    public ReportEvaluationInfoBuilder isSharedReport(final boolean isSharedReport) {
      this.reportEvaluationInfo.setSharedReport(isSharedReport);
      return this;
    }

    public ReportEvaluationInfo build() {
      return this.reportEvaluationInfo;
    }

  }

}
