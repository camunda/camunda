/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.exec;

import lombok.Data;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;
import org.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;
import org.camunda.optimize.service.es.report.MinMaxStatDto;
import org.camunda.optimize.service.es.report.ReportEvaluationContext;
import org.elasticsearch.index.query.QueryBuilder;

import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static java.util.stream.Collectors.toMap;

@Data
public class ExecutionContext<ReportData extends SingleReportDataDto> {

  private ReportData reportData;
  private ZoneId timezone;
  private long unfilteredInstanceCount;
  private PaginationDto pagination;
  private boolean isExport;

  // used in the context of combined reports to establish identical bucket sizes/ranges across all single reports
  private MinMaxStatDto combinedRangeMinMaxStats;

  // used for distributed reports which need to create minMaxStats based on the baseQuery (eg for variable or date
  // ranges)
  private QueryBuilder distributedByMinMaxBaseQuery;

  // used to ensure a complete list of distributedByResults (to include all keys, even if the result is empty)
  // e.g. used for groupBy usertask - distributedBy assignee reports, where it is possible that
  // a user has been assigned to one userTask but not another, yet we want the userId to appear
  // in all groupByResults (with 0 if they have not been assigned to said task)
  private Map<String, String> allDistributedByKeysAndLabels = new HashMap<>();

  // used to ensure a complete list of variable names can be used to enrich report results that use pagination
  // For example, we include variables for all instances in a raw data report, even if none of the instances in view
  // have a value for that variable
  private Set<String> allVariablesNames = new HashSet<>();

  public <RD extends ReportDefinitionDto<ReportData>> ExecutionContext(final ReportEvaluationContext<RD> reportEvaluationContext) {
    this.reportData = reportEvaluationContext.getReportDefinition().getData();
    this.timezone = reportEvaluationContext.getTimezone();
    this.combinedRangeMinMaxStats = reportEvaluationContext.getCombinedRangeMinMaxStats();
    this.pagination = reportEvaluationContext.getPagination();
    this.isExport = reportEvaluationContext.isExport();
  }

  public SingleReportConfigurationDto getReportConfiguration() {
    return reportData.getConfiguration();
  }

  public Optional<MinMaxStatDto> getCombinedRangeMinMaxStats() {
    return Optional.ofNullable(combinedRangeMinMaxStats);
  }

  public void setAllDistributedByKeys(final Set<String> allDistributedByKeys) {
    this.allDistributedByKeysAndLabels = allDistributedByKeys
      .stream()
      .collect(toMap(Function.identity(), Function.identity()));
  }

}
