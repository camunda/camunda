/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report;

import static java.util.stream.Collectors.toMap;

import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;
import io.camunda.optimize.service.db.filter.FilterContext;
import io.camunda.optimize.service.db.report.plan.ExecutionPlan;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public class ExecutionContext<D extends SingleReportDataDto, P extends ExecutionPlan> {

  private P plan;
  private D reportData;
  private ZoneId timezone;
  private long unfilteredTotalInstanceCount;
  private Optional<PaginationDto> pagination;
  private boolean isCsvExport;
  private boolean isJsonExport;

  // used in the context of combined reports to establish identical bucket sizes/ranges across all
  // single reports
  private MinMaxStatDto combinedRangeMinMaxStats;

  // used to ensure a complete list of distributedByResults (to include all keys, even if the result
  // is empty)
  // e.g. used for groupBy usertask - distributedBy assignee reports, where it is possible that
  // a user has been assigned to one userTask but not another, yet we want the userId to appear
  // in all groupByResults (with 0 if they have not been assigned to said task)
  private Map<String, String> allDistributedByKeysAndLabels = new HashMap<>();

  // used to ensure a complete list of variable names can be used to enrich report results that use
  // pagination
  // For example, we include variables for all instances in a raw data report, even if none of the
  // instances in view
  // have a value for that variable
  private Set<String> allVariablesNames = new HashSet<>();

  // For heatmap reports, we exclude the collapsed subprocess data from being visualised. The keys
  // for these nodes are calculated up front and removed during result retrieval
  private Set<String> hiddenFlowNodeIds = new HashSet<>();

  private FilterContext filterContext;

  private boolean multiIndexAlias = false;

  public <R extends ReportDefinitionDto<D>> ExecutionContext(
      final ReportEvaluationContext<? extends R> reportEvaluationContext) {
    this(reportEvaluationContext, null);
  }

  public <R extends ReportDefinitionDto<D>> ExecutionContext(
      final ReportEvaluationContext<? extends R> reportEvaluationContext, final P plan) {
    this.plan = plan;
    reportData = reportEvaluationContext.getReportDefinition().getData();
    timezone = reportEvaluationContext.getTimezone();
    combinedRangeMinMaxStats = reportEvaluationContext.getCombinedRangeMinMaxStats();
    pagination = reportEvaluationContext.getPagination();
    isCsvExport = reportEvaluationContext.isCsvExport();
    filterContext = createFilterContext(reportEvaluationContext);
    isJsonExport = reportEvaluationContext.isJsonExport();
    hiddenFlowNodeIds = reportEvaluationContext.getHiddenFlowNodeIds();
  }

  public SingleReportConfigurationDto getReportConfiguration() {
    return reportData.getConfiguration();
  }

  public Optional<MinMaxStatDto> getCombinedRangeMinMaxStats() {
    return Optional.ofNullable(combinedRangeMinMaxStats);
  }

  public void setAllDistributedByKeys(final Set<String> allDistributedByKeys) {
    allDistributedByKeysAndLabels =
        allDistributedByKeys.stream().collect(toMap(Function.identity(), Function.identity()));
  }

  private <R extends ReportDefinitionDto<D>> FilterContext createFilterContext(
      final ReportEvaluationContext<R> reportEvaluationContext) {
    final FilterContext.FilterContextBuilder builder =
        FilterContext.builder().timezone(reportEvaluationContext.getTimezone());
    final D definitionReportData = reportEvaluationContext.getReportDefinition().getData();
    // not nice to care about type specifics here but it is still the best place to fit this general
    // mapping
    if (definitionReportData instanceof ProcessReportDataDto) {
      builder.userTaskReport(((ProcessReportDataDto) definitionReportData).isUserTaskReport());
    }
    return builder.build();
  }

  public P getPlan() {
    return this.plan;
  }

  public D getReportData() {
    return this.reportData;
  }

  public ZoneId getTimezone() {
    return this.timezone;
  }

  public long getUnfilteredTotalInstanceCount() {
    return this.unfilteredTotalInstanceCount;
  }

  public Optional<PaginationDto> getPagination() {
    return this.pagination;
  }

  public boolean isCsvExport() {
    return this.isCsvExport;
  }

  public boolean isJsonExport() {
    return this.isJsonExport;
  }

  public Map<String, String> getAllDistributedByKeysAndLabels() {
    return this.allDistributedByKeysAndLabels;
  }

  public Set<String> getAllVariablesNames() {
    return this.allVariablesNames;
  }

  public Set<String> getHiddenFlowNodeIds() {
    return this.hiddenFlowNodeIds;
  }

  public FilterContext getFilterContext() {
    return this.filterContext;
  }

  public boolean isMultiIndexAlias() {
    return this.multiIndexAlias;
  }

  public void setPlan(P plan) {
    this.plan = plan;
  }

  public void setReportData(D reportData) {
    this.reportData = reportData;
  }

  public void setTimezone(ZoneId timezone) {
    this.timezone = timezone;
  }

  public void setUnfilteredTotalInstanceCount(long unfilteredTotalInstanceCount) {
    this.unfilteredTotalInstanceCount = unfilteredTotalInstanceCount;
  }

  public void setPagination(Optional<PaginationDto> pagination) {
    this.pagination = pagination;
  }

  public void setCsvExport(boolean isCsvExport) {
    this.isCsvExport = isCsvExport;
  }

  public void setJsonExport(boolean isJsonExport) {
    this.isJsonExport = isJsonExport;
  }

  public void setCombinedRangeMinMaxStats(MinMaxStatDto combinedRangeMinMaxStats) {
    this.combinedRangeMinMaxStats = combinedRangeMinMaxStats;
  }

  public void setAllDistributedByKeysAndLabels(Map<String, String> allDistributedByKeysAndLabels) {
    this.allDistributedByKeysAndLabels = allDistributedByKeysAndLabels;
  }

  public void setAllVariablesNames(Set<String> allVariablesNames) {
    this.allVariablesNames = allVariablesNames;
  }

  public void setHiddenFlowNodeIds(Set<String> hiddenFlowNodeIds) {
    this.hiddenFlowNodeIds = hiddenFlowNodeIds;
  }

  public void setFilterContext(FilterContext filterContext) {
    this.filterContext = filterContext;
  }

  public void setMultiIndexAlias(boolean multiIndexAlias) {
    this.multiIndexAlias = multiIndexAlias;
  }

  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof ExecutionContext)) {
      return false;
    }
    final ExecutionContext<?, ?> other = (ExecutionContext<?, ?>) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$plan = this.getPlan();
    final Object other$plan = other.getPlan();
    if (this$plan == null ? other$plan != null : !this$plan.equals(other$plan)) {
      return false;
    }
    final Object this$reportData = this.getReportData();
    final Object other$reportData = other.getReportData();
    if (this$reportData == null
        ? other$reportData != null
        : !this$reportData.equals(other$reportData)) {
      return false;
    }
    final Object this$timezone = this.getTimezone();
    final Object other$timezone = other.getTimezone();
    if (this$timezone == null ? other$timezone != null : !this$timezone.equals(other$timezone)) {
      return false;
    }
    if (this.getUnfilteredTotalInstanceCount() != other.getUnfilteredTotalInstanceCount()) {
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
    final Object this$combinedRangeMinMaxStats = this.getCombinedRangeMinMaxStats();
    final Object other$combinedRangeMinMaxStats = other.getCombinedRangeMinMaxStats();
    if (this$combinedRangeMinMaxStats == null
        ? other$combinedRangeMinMaxStats != null
        : !this$combinedRangeMinMaxStats.equals(other$combinedRangeMinMaxStats)) {
      return false;
    }
    final Object this$allDistributedByKeysAndLabels = this.getAllDistributedByKeysAndLabels();
    final Object other$allDistributedByKeysAndLabels = other.getAllDistributedByKeysAndLabels();
    if (this$allDistributedByKeysAndLabels == null
        ? other$allDistributedByKeysAndLabels != null
        : !this$allDistributedByKeysAndLabels.equals(other$allDistributedByKeysAndLabels)) {
      return false;
    }
    final Object this$allVariablesNames = this.getAllVariablesNames();
    final Object other$allVariablesNames = other.getAllVariablesNames();
    if (this$allVariablesNames == null
        ? other$allVariablesNames != null
        : !this$allVariablesNames.equals(other$allVariablesNames)) {
      return false;
    }
    final Object this$hiddenFlowNodeIds = this.getHiddenFlowNodeIds();
    final Object other$hiddenFlowNodeIds = other.getHiddenFlowNodeIds();
    if (this$hiddenFlowNodeIds == null
        ? other$hiddenFlowNodeIds != null
        : !this$hiddenFlowNodeIds.equals(other$hiddenFlowNodeIds)) {
      return false;
    }
    final Object this$filterContext = this.getFilterContext();
    final Object other$filterContext = other.getFilterContext();
    if (this$filterContext == null
        ? other$filterContext != null
        : !this$filterContext.equals(other$filterContext)) {
      return false;
    }
    if (this.isMultiIndexAlias() != other.isMultiIndexAlias()) {
      return false;
    }
    return true;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ExecutionContext;
  }

  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $plan = this.getPlan();
    result = result * PRIME + ($plan == null ? 43 : $plan.hashCode());
    final Object $reportData = this.getReportData();
    result = result * PRIME + ($reportData == null ? 43 : $reportData.hashCode());
    final Object $timezone = this.getTimezone();
    result = result * PRIME + ($timezone == null ? 43 : $timezone.hashCode());
    final long $unfilteredTotalInstanceCount = this.getUnfilteredTotalInstanceCount();
    result =
        result * PRIME
            + (int) ($unfilteredTotalInstanceCount >>> 32 ^ $unfilteredTotalInstanceCount);
    final Object $pagination = this.getPagination();
    result = result * PRIME + ($pagination == null ? 43 : $pagination.hashCode());
    result = result * PRIME + (this.isCsvExport() ? 79 : 97);
    result = result * PRIME + (this.isJsonExport() ? 79 : 97);
    final Object $combinedRangeMinMaxStats = this.getCombinedRangeMinMaxStats();
    result =
        result * PRIME
            + ($combinedRangeMinMaxStats == null ? 43 : $combinedRangeMinMaxStats.hashCode());
    final Object $allDistributedByKeysAndLabels = this.getAllDistributedByKeysAndLabels();
    result =
        result * PRIME
            + ($allDistributedByKeysAndLabels == null
                ? 43
                : $allDistributedByKeysAndLabels.hashCode());
    final Object $allVariablesNames = this.getAllVariablesNames();
    result = result * PRIME + ($allVariablesNames == null ? 43 : $allVariablesNames.hashCode());
    final Object $hiddenFlowNodeIds = this.getHiddenFlowNodeIds();
    result = result * PRIME + ($hiddenFlowNodeIds == null ? 43 : $hiddenFlowNodeIds.hashCode());
    final Object $filterContext = this.getFilterContext();
    result = result * PRIME + ($filterContext == null ? 43 : $filterContext.hashCode());
    result = result * PRIME + (this.isMultiIndexAlias() ? 79 : 97);
    return result;
  }

  public String toString() {
    return "ExecutionContext(plan="
        + this.getPlan()
        + ", reportData="
        + this.getReportData()
        + ", timezone="
        + this.getTimezone()
        + ", unfilteredTotalInstanceCount="
        + this.getUnfilteredTotalInstanceCount()
        + ", pagination="
        + this.getPagination()
        + ", isCsvExport="
        + this.isCsvExport()
        + ", isJsonExport="
        + this.isJsonExport()
        + ", combinedRangeMinMaxStats="
        + this.getCombinedRangeMinMaxStats()
        + ", allDistributedByKeysAndLabels="
        + this.getAllDistributedByKeysAndLabels()
        + ", allVariablesNames="
        + this.getAllVariablesNames()
        + ", hiddenFlowNodeIds="
        + this.getHiddenFlowNodeIds()
        + ", filterContext="
        + this.getFilterContext()
        + ", multiIndexAlias="
        + this.isMultiIndexAlias()
        + ")";
  }
}
