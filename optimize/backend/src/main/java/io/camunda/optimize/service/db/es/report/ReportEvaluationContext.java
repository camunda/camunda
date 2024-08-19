/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report;

import static io.camunda.optimize.util.SuppressionConstants.UNCHECKED_CAST;

import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;
import java.time.ZoneId;
import java.util.Optional;

public class ReportEvaluationContext<R extends ReportDefinitionDto<?>> {

  private R reportDefinition;
  private PaginationDto pagination;
  private boolean isCsvExport;
  private boolean isJsonExport;

  // used in the context of combined reports to establish identical bucket sizes/ranges across all
  // single reports
  private MinMaxStatDto combinedRangeMinMaxStats;

  // users can define which timezone the date data should be based on
  private ZoneId timezone = ZoneId.systemDefault();

  public ReportEvaluationContext() {}

  public Optional<PaginationDto> getPagination() {
    return Optional.ofNullable(pagination);
  }

  public void setPagination(final PaginationDto pagination) {
    this.pagination = pagination;
  }

  @SuppressWarnings(UNCHECKED_CAST)
  public static <R extends ReportDefinitionDto<?>> ReportEvaluationContext<R> fromReportEvaluation(
      final ReportEvaluationInfo evaluationInfo) {
    final ReportEvaluationContext<R> context = new ReportEvaluationContext<>();
    context.setReportDefinition((R) evaluationInfo.getReport());
    context.setTimezone(evaluationInfo.getTimezone());
    context.setPagination(evaluationInfo.getPagination().orElse(null));
    context.setCsvExport(evaluationInfo.isCsvExport());
    context.setJsonExport(evaluationInfo.isJsonExport());
    return context;
  }

  public R getReportDefinition() {
    return reportDefinition;
  }

  public void setReportDefinition(final R reportDefinition) {
    this.reportDefinition = reportDefinition;
  }

  public boolean isCsvExport() {
    return isCsvExport;
  }

  public void setCsvExport(final boolean isCsvExport) {
    this.isCsvExport = isCsvExport;
  }

  public boolean isJsonExport() {
    return isJsonExport;
  }

  public void setJsonExport(final boolean isJsonExport) {
    this.isJsonExport = isJsonExport;
  }

  public MinMaxStatDto getCombinedRangeMinMaxStats() {
    return combinedRangeMinMaxStats;
  }

  public void setCombinedRangeMinMaxStats(final MinMaxStatDto combinedRangeMinMaxStats) {
    this.combinedRangeMinMaxStats = combinedRangeMinMaxStats;
  }

  public ZoneId getTimezone() {
    return timezone;
  }

  public void setTimezone(final ZoneId timezone) {
    this.timezone = timezone;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ReportEvaluationContext;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $reportDefinition = getReportDefinition();
    result = result * PRIME + ($reportDefinition == null ? 43 : $reportDefinition.hashCode());
    final Object $pagination = getPagination();
    result = result * PRIME + ($pagination == null ? 43 : $pagination.hashCode());
    result = result * PRIME + (isCsvExport() ? 79 : 97);
    result = result * PRIME + (isJsonExport() ? 79 : 97);
    final Object $combinedRangeMinMaxStats = getCombinedRangeMinMaxStats();
    result =
        result * PRIME
            + ($combinedRangeMinMaxStats == null ? 43 : $combinedRangeMinMaxStats.hashCode());
    final Object $timezone = getTimezone();
    result = result * PRIME + ($timezone == null ? 43 : $timezone.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof ReportEvaluationContext)) {
      return false;
    }
    final ReportEvaluationContext<?> other = (ReportEvaluationContext<?>) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$reportDefinition = getReportDefinition();
    final Object other$reportDefinition = other.getReportDefinition();
    if (this$reportDefinition == null
        ? other$reportDefinition != null
        : !this$reportDefinition.equals(other$reportDefinition)) {
      return false;
    }
    final Object this$pagination = getPagination();
    final Object other$pagination = other.getPagination();
    if (this$pagination == null
        ? other$pagination != null
        : !this$pagination.equals(other$pagination)) {
      return false;
    }
    if (isCsvExport() != other.isCsvExport()) {
      return false;
    }
    if (isJsonExport() != other.isJsonExport()) {
      return false;
    }
    final Object this$combinedRangeMinMaxStats = getCombinedRangeMinMaxStats();
    final Object other$combinedRangeMinMaxStats = other.getCombinedRangeMinMaxStats();
    if (this$combinedRangeMinMaxStats == null
        ? other$combinedRangeMinMaxStats != null
        : !this$combinedRangeMinMaxStats.equals(other$combinedRangeMinMaxStats)) {
      return false;
    }
    final Object this$timezone = getTimezone();
    final Object other$timezone = other.getTimezone();
    if (this$timezone == null ? other$timezone != null : !this$timezone.equals(other$timezone)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "ReportEvaluationContext(reportDefinition="
        + getReportDefinition()
        + ", pagination="
        + getPagination()
        + ", isCsvExport="
        + isCsvExport()
        + ", isJsonExport="
        + isJsonExport()
        + ", combinedRangeMinMaxStats="
        + getCombinedRangeMinMaxStats()
        + ", timezone="
        + getTimezone()
        + ")";
  }
}
