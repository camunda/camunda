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

  public void setReportDefinition(final R reportDefinition) {
    this.reportDefinition = reportDefinition;
  }

  public void setPagination(final PaginationDto pagination) {
    this.pagination = pagination;
  }

  public void setCsvExport(final boolean isCsvExport) {
    this.isCsvExport = isCsvExport;
  }

  public void setJsonExport(final boolean isJsonExport) {
    this.isJsonExport = isJsonExport;
  }

  public void setHiddenFlowNodeIds(final Set<String> hiddenFlowNodeIds) {
    this.hiddenFlowNodeIds = hiddenFlowNodeIds;
  }

  public void setCombinedRangeMinMaxStats(final MinMaxStatDto combinedRangeMinMaxStats) {
    this.combinedRangeMinMaxStats = combinedRangeMinMaxStats;
  }

  public void setTimezone(final ZoneId timezone) {
    this.timezone = timezone;
  }

  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ReportEvaluationContext;
  }

  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
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
