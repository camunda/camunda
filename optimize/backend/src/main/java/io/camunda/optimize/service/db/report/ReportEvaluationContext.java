/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report;

import static io.camunda.optimize.util.SuppressionConstants.UNCHECKED_CAST;

import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;

public class ReportEvaluationContext<R extends ReportDefinitionDto<?>> {

  private R reportDefinition;
  private PaginationDto pagination;
  private boolean isCsvExport;
  private boolean isJsonExport;
  private Set<String> hiddenFlowNodeIds;

  // used in the context of combined reports to establish identical bucket sizes/ranges across all
  // single reports
  private MinMaxStatDto combinedRangeMinMaxStats;

  // users can define which timezone the date data should be based on
  private ZoneId timezone = ZoneId.systemDefault();

  public ReportEvaluationContext() {}

  public Optional<PaginationDto> getPagination() {
    return Optional.ofNullable(pagination);
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
    context.setHiddenFlowNodeIds(evaluationInfo.getHiddenFlowNodeIds());
    return context;
  }

  public R getReportDefinition() {
    return this.reportDefinition;
  }

  public boolean isCsvExport() {
    return this.isCsvExport;
  }

  public boolean isJsonExport() {
    return this.isJsonExport;
  }

  public Set<String> getHiddenFlowNodeIds() {
    return this.hiddenFlowNodeIds;
  }

  public MinMaxStatDto getCombinedRangeMinMaxStats() {
    return this.combinedRangeMinMaxStats;
  }

  public ZoneId getTimezone() {
    return this.timezone;
  }

  public void setReportDefinition(R reportDefinition) {
    this.reportDefinition = reportDefinition;
  }

  public void setPagination(PaginationDto pagination) {
    this.pagination = pagination;
  }

  public void setCsvExport(boolean isCsvExport) {
    this.isCsvExport = isCsvExport;
  }

  public void setJsonExport(boolean isJsonExport) {
    this.isJsonExport = isJsonExport;
  }

  public void setHiddenFlowNodeIds(Set<String> hiddenFlowNodeIds) {
    this.hiddenFlowNodeIds = hiddenFlowNodeIds;
  }

  public void setCombinedRangeMinMaxStats(MinMaxStatDto combinedRangeMinMaxStats) {
    this.combinedRangeMinMaxStats = combinedRangeMinMaxStats;
  }

  public void setTimezone(ZoneId timezone) {
    this.timezone = timezone;
  }

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
    final Object this$reportDefinition = this.getReportDefinition();
    final Object other$reportDefinition = other.getReportDefinition();
    if (this$reportDefinition == null
        ? other$reportDefinition != null
        : !this$reportDefinition.equals(other$reportDefinition)) {
      return false;
    }
    final Object this$pagination = this.getPagination();
    final Object other$pagination = other.getPagination();
    if (this$pagination == null
        ? other$pagination != null
        : !this$pagination.equals(other$pagination)) {
      return false;
    }
    if (this.isCsvExport() != other.isCsvExport()) {
      return false;
    }
    if (this.isJsonExport() != other.isJsonExport()) {
      return false;
    }
    final Object this$hiddenFlowNodeIds = this.getHiddenFlowNodeIds();
    final Object other$hiddenFlowNodeIds = other.getHiddenFlowNodeIds();
    if (this$hiddenFlowNodeIds == null
        ? other$hiddenFlowNodeIds != null
        : !this$hiddenFlowNodeIds.equals(other$hiddenFlowNodeIds)) {
      return false;
    }
    final Object this$combinedRangeMinMaxStats = this.getCombinedRangeMinMaxStats();
    final Object other$combinedRangeMinMaxStats = other.getCombinedRangeMinMaxStats();
    if (this$combinedRangeMinMaxStats == null
        ? other$combinedRangeMinMaxStats != null
        : !this$combinedRangeMinMaxStats.equals(other$combinedRangeMinMaxStats)) {
      return false;
    }
    final Object this$timezone = this.getTimezone();
    final Object other$timezone = other.getTimezone();
    if (this$timezone == null ? other$timezone != null : !this$timezone.equals(other$timezone)) {
      return false;
    }
    return true;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ReportEvaluationContext;
  }

  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $reportDefinition = this.getReportDefinition();
    result = result * PRIME + ($reportDefinition == null ? 43 : $reportDefinition.hashCode());
    final Object $pagination = this.getPagination();
    result = result * PRIME + ($pagination == null ? 43 : $pagination.hashCode());
    result = result * PRIME + (this.isCsvExport() ? 79 : 97);
    result = result * PRIME + (this.isJsonExport() ? 79 : 97);
    final Object $hiddenFlowNodeIds = this.getHiddenFlowNodeIds();
    result = result * PRIME + ($hiddenFlowNodeIds == null ? 43 : $hiddenFlowNodeIds.hashCode());
    final Object $combinedRangeMinMaxStats = this.getCombinedRangeMinMaxStats();
    result =
        result * PRIME
            + ($combinedRangeMinMaxStats == null ? 43 : $combinedRangeMinMaxStats.hashCode());
    final Object $timezone = this.getTimezone();
    result = result * PRIME + ($timezone == null ? 43 : $timezone.hashCode());
    return result;
  }

  public String toString() {
    return "ReportEvaluationContext(reportDefinition="
        + this.getReportDefinition()
        + ", pagination="
        + this.getPagination()
        + ", isCsvExport="
        + this.isCsvExport()
        + ", isJsonExport="
        + this.isJsonExport()
        + ", hiddenFlowNodeIds="
        + this.getHiddenFlowNodeIds()
        + ", combinedRangeMinMaxStats="
        + this.getCombinedRangeMinMaxStats()
        + ", timezone="
        + this.getTimezone()
        + ")";
  }
}
