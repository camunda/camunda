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
import org.camunda.optimize.service.es.report.command.CommandContext;
import org.elasticsearch.index.query.QueryBuilder;

import java.time.ZoneId;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

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
  private Set<String> allDistributedByKeys = new HashSet<>();

  public <RD extends ReportDefinitionDto<ReportData>> ExecutionContext(final CommandContext<RD> commandContext) {
    this.reportData = commandContext.getReportDefinition().getData();
    this.timezone = commandContext.getTimezone();
    this.combinedRangeMinMaxStats = commandContext.getCombinedRangeMinMaxStats();
    this.pagination = commandContext.getPagination();
    this.isExport = commandContext.isExport();
  }

  public SingleReportConfigurationDto getReportConfiguration() {
    return reportData.getConfiguration();
  }

  public Optional<MinMaxStatDto> getCombinedRangeMinMaxStats() {
    return Optional.ofNullable(combinedRangeMinMaxStats);
  }

}
