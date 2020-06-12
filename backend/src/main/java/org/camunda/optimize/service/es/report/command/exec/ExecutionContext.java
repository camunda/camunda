/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.exec;

import lombok.Data;
import org.apache.commons.lang3.Range;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.SingleReportConfigurationDto;
import org.camunda.optimize.service.es.report.command.CommandContext;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;

@Data
public class ExecutionContext<ReportData extends SingleReportDataDto> {

  private ReportData reportData;
  private Integer recordLimit;

  // only used/needed for group by date commands when evaluated for
  // a combined report.
  private Range<OffsetDateTime> dateIntervalRange;

  // only used for group by number variable commands when evaluated for a combined report
  private Range<Double> numberVariableRange;

  // used to ensure a complete list of distributedByResults (to include all keys, even if the result is empty)
  // e.g. used for groupBy usertask - distributedBy assignee reports, where it is possible that
  // a user has been assigned to one userTask but not another, yet we want the userId to appear
  // in all groupByResults (with 0 if they have not been assigned to said task)
  private Set<String> allDistributedByKeys;

  public <RD extends ReportDefinitionDto<ReportData>> ExecutionContext(final CommandContext<RD> commandContext) {
    this.reportData = commandContext.getReportDefinition().getData();
    this.recordLimit = commandContext.getRecordLimit();
    this.dateIntervalRange = commandContext.getDateIntervalRange();
    this.numberVariableRange = commandContext.getNumberVariableRange();
  }

  public SingleReportConfigurationDto getReportConfiguration() {
    return reportData.getConfiguration();
  }

  public Optional<Range<Double>> getNumberVariableRange() {
    return Optional.ofNullable(numberVariableRange);
  }

  public Optional<Range<OffsetDateTime>> getDateIntervalRange() {
    return Optional.ofNullable(dateIntervalRange);
  }

}
