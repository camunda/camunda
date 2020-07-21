/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.camunda.optimize.dto.optimize.query.report.AdditionalProcessReportEvaluationFilterDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.service.es.reader.ReportReader;

import javax.ws.rs.NotFoundException;
import java.time.ZoneId;

import static org.camunda.optimize.service.es.report.SingleReportEvaluator.DEFAULT_RECORD_LIMIT;

@Getter
@Setter(AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ReportEvaluationInfo {

  private ReportDefinitionDto report;
  private String reportId;

  private String userId;
  private Integer customRecordLimit = DEFAULT_RECORD_LIMIT;
  private AdditionalProcessReportEvaluationFilterDto additionalFilters =
    new AdditionalProcessReportEvaluationFilterDto();
  private ZoneId timezone = ZoneId.systemDefault();

  public void postFetchSavedReport(final ReportReader reportReader) {
    if (reportId != null) {
      report = reportReader.getReport(reportId)
        .orElseThrow(() -> new NotFoundException("Report with id [" + reportId + "] does not exist"));
    }
  }

  public static ReportEvaluationInfoBuilder builder(final ReportDefinitionDto report) {
    ReportEvaluationInfo reportEvaluationInfo = new ReportEvaluationInfo();
    reportEvaluationInfo.setReport(report);
    return new ReportEvaluationInfoBuilder(reportEvaluationInfo);
  }

  public static ReportEvaluationInfoBuilder builder(final String reportId) {
    ReportEvaluationInfo reportEvaluationInfo = new ReportEvaluationInfo();
    reportEvaluationInfo.setReportId(reportId);
    return  new ReportEvaluationInfoBuilder(reportEvaluationInfo);
  }

  @RequiredArgsConstructor
  public static class ReportEvaluationInfoBuilder {

    private final ReportEvaluationInfo reportEvaluationInfo;

    public ReportEvaluationInfoBuilder userId(final String userId) {
      this.reportEvaluationInfo.setUserId(userId);
      return this;
    }

    public ReportEvaluationInfoBuilder customRecordLimit(final Integer customRecordLimit) {
      this.reportEvaluationInfo.setCustomRecordLimit(customRecordLimit);
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

    public ReportEvaluationInfo build() {
      return this.reportEvaluationInfo;
    }

  }

}
