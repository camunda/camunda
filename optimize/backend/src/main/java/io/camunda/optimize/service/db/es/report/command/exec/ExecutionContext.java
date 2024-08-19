/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.command.exec;

import static java.util.stream.Collectors.toMap;

import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;
import io.camunda.optimize.service.db.es.filter.FilterContext;
import io.camunda.optimize.service.db.es.report.MinMaxStatDto;
import io.camunda.optimize.service.db.es.report.ReportEvaluationContext;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.elasticsearch.index.query.QueryBuilder;

public class ExecutionContext<D extends SingleReportDataDto> {

  private D reportData;
  private ZoneId timezone;
  private long unfilteredTotalInstanceCount;
  private Optional<PaginationDto> pagination;
  private boolean isCsvExport;
  private boolean isJsonExport;

  // used in the context of combined reports to establish identical bucket sizes/ranges across all
  // single reports
  private MinMaxStatDto combinedRangeMinMaxStats;

  // used for distributed reports which need to create minMaxStats based on the baseQuery (eg for
  // variable or date
  // ranges)
  private QueryBuilder distributedByMinMaxBaseQuery;

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

  private FilterContext filterContext;

  public <R extends ReportDefinitionDto<D>> ExecutionContext(
      final ReportEvaluationContext<R> reportEvaluationContext) {
    reportData = reportEvaluationContext.getReportDefinition().getData();
    timezone = reportEvaluationContext.getTimezone();
    combinedRangeMinMaxStats = reportEvaluationContext.getCombinedRangeMinMaxStats();
    pagination = reportEvaluationContext.getPagination();
    isCsvExport = reportEvaluationContext.isCsvExport();
    filterContext = createFilterContext(reportEvaluationContext);
    isJsonExport = reportEvaluationContext.isJsonExport();
  }

  public SingleReportConfigurationDto getReportConfiguration() {
    return reportData.getConfiguration();
  }

  public Optional<MinMaxStatDto> getCombinedRangeMinMaxStats() {
    return Optional.ofNullable(combinedRangeMinMaxStats);
  }

  public void setCombinedRangeMinMaxStats(final MinMaxStatDto combinedRangeMinMaxStats) {
    this.combinedRangeMinMaxStats = combinedRangeMinMaxStats;
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

  public D getReportData() {
    return reportData;
  }

  public void setReportData(final D reportData) {
    this.reportData = reportData;
  }

  public ZoneId getTimezone() {
    return timezone;
  }

  public void setTimezone(final ZoneId timezone) {
    this.timezone = timezone;
  }

  public long getUnfilteredTotalInstanceCount() {
    return unfilteredTotalInstanceCount;
  }

  public void setUnfilteredTotalInstanceCount(final long unfilteredTotalInstanceCount) {
    this.unfilteredTotalInstanceCount = unfilteredTotalInstanceCount;
  }

  public Optional<PaginationDto> getPagination() {
    return pagination;
  }

  public void setPagination(final Optional<PaginationDto> pagination) {
    this.pagination = pagination;
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

  public QueryBuilder getDistributedByMinMaxBaseQuery() {
    return distributedByMinMaxBaseQuery;
  }

  public void setDistributedByMinMaxBaseQuery(final QueryBuilder distributedByMinMaxBaseQuery) {
    this.distributedByMinMaxBaseQuery = distributedByMinMaxBaseQuery;
  }

  public Map<String, String> getAllDistributedByKeysAndLabels() {
    return allDistributedByKeysAndLabels;
  }

  public void setAllDistributedByKeysAndLabels(
      final Map<String, String> allDistributedByKeysAndLabels) {
    this.allDistributedByKeysAndLabels = allDistributedByKeysAndLabels;
  }

  public Set<String> getAllVariablesNames() {
    return allVariablesNames;
  }

  public void setAllVariablesNames(final Set<String> allVariablesNames) {
    this.allVariablesNames = allVariablesNames;
  }

  public FilterContext getFilterContext() {
    return filterContext;
  }

  public void setFilterContext(final FilterContext filterContext) {
    this.filterContext = filterContext;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ExecutionContext;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $reportData = getReportData();
    result = result * PRIME + ($reportData == null ? 43 : $reportData.hashCode());
    final Object $timezone = getTimezone();
    result = result * PRIME + ($timezone == null ? 43 : $timezone.hashCode());
    final long $unfilteredTotalInstanceCount = getUnfilteredTotalInstanceCount();
    result =
        result * PRIME
            + (int) ($unfilteredTotalInstanceCount >>> 32 ^ $unfilteredTotalInstanceCount);
    final Object $pagination = getPagination();
    result = result * PRIME + ($pagination == null ? 43 : $pagination.hashCode());
    result = result * PRIME + (isCsvExport() ? 79 : 97);
    result = result * PRIME + (isJsonExport() ? 79 : 97);
    final Object $combinedRangeMinMaxStats = getCombinedRangeMinMaxStats();
    result =
        result * PRIME
            + ($combinedRangeMinMaxStats == null ? 43 : $combinedRangeMinMaxStats.hashCode());
    final Object $distributedByMinMaxBaseQuery = getDistributedByMinMaxBaseQuery();
    result =
        result * PRIME
            + ($distributedByMinMaxBaseQuery == null
                ? 43
                : $distributedByMinMaxBaseQuery.hashCode());
    final Object $allDistributedByKeysAndLabels = getAllDistributedByKeysAndLabels();
    result =
        result * PRIME
            + ($allDistributedByKeysAndLabels == null
                ? 43
                : $allDistributedByKeysAndLabels.hashCode());
    final Object $allVariablesNames = getAllVariablesNames();
    result = result * PRIME + ($allVariablesNames == null ? 43 : $allVariablesNames.hashCode());
    final Object $filterContext = getFilterContext();
    result = result * PRIME + ($filterContext == null ? 43 : $filterContext.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof ExecutionContext)) {
      return false;
    }
    final ExecutionContext<?> other = (ExecutionContext<?>) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$reportData = getReportData();
    final Object other$reportData = other.getReportData();
    if (this$reportData == null
        ? other$reportData != null
        : !this$reportData.equals(other$reportData)) {
      return false;
    }
    final Object this$timezone = getTimezone();
    final Object other$timezone = other.getTimezone();
    if (this$timezone == null ? other$timezone != null : !this$timezone.equals(other$timezone)) {
      return false;
    }
    if (getUnfilteredTotalInstanceCount() != other.getUnfilteredTotalInstanceCount()) {
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
    final Object this$distributedByMinMaxBaseQuery = getDistributedByMinMaxBaseQuery();
    final Object other$distributedByMinMaxBaseQuery = other.getDistributedByMinMaxBaseQuery();
    if (this$distributedByMinMaxBaseQuery == null
        ? other$distributedByMinMaxBaseQuery != null
        : !this$distributedByMinMaxBaseQuery.equals(other$distributedByMinMaxBaseQuery)) {
      return false;
    }
    final Object this$allDistributedByKeysAndLabels = getAllDistributedByKeysAndLabels();
    final Object other$allDistributedByKeysAndLabels = other.getAllDistributedByKeysAndLabels();
    if (this$allDistributedByKeysAndLabels == null
        ? other$allDistributedByKeysAndLabels != null
        : !this$allDistributedByKeysAndLabels.equals(other$allDistributedByKeysAndLabels)) {
      return false;
    }
    final Object this$allVariablesNames = getAllVariablesNames();
    final Object other$allVariablesNames = other.getAllVariablesNames();
    if (this$allVariablesNames == null
        ? other$allVariablesNames != null
        : !this$allVariablesNames.equals(other$allVariablesNames)) {
      return false;
    }
    final Object this$filterContext = getFilterContext();
    final Object other$filterContext = other.getFilterContext();
    if (this$filterContext == null
        ? other$filterContext != null
        : !this$filterContext.equals(other$filterContext)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "ExecutionContext(reportData="
        + getReportData()
        + ", timezone="
        + getTimezone()
        + ", unfilteredTotalInstanceCount="
        + getUnfilteredTotalInstanceCount()
        + ", pagination="
        + getPagination()
        + ", isCsvExport="
        + isCsvExport()
        + ", isJsonExport="
        + isJsonExport()
        + ", combinedRangeMinMaxStats="
        + getCombinedRangeMinMaxStats()
        + ", distributedByMinMaxBaseQuery="
        + getDistributedByMinMaxBaseQuery()
        + ", allDistributedByKeysAndLabels="
        + getAllDistributedByKeysAndLabels()
        + ", allVariablesNames="
        + getAllVariablesNames()
        + ", filterContext="
        + getFilterContext()
        + ")";
  }
}
