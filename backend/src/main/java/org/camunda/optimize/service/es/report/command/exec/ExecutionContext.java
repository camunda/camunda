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

@Data
public class ExecutionContext<ReportData extends SingleReportDataDto> {

  private ReportData reportData;
  private Integer recordLimit;

  // only used/needed for group by date commands when evaluated for
  // a combined report.
  private Range<OffsetDateTime> dateIntervalRange;

  public <RD extends ReportDefinitionDto<ReportData>> ExecutionContext(final CommandContext<RD> commandContext) {
    this.reportData = commandContext.getReportDefinition().getData();
    this.recordLimit = commandContext.getRecordLimit();
    this.dateIntervalRange = commandContext.getDateIntervalRange();
  }

  public SingleReportConfigurationDto getReportConfiguration() {
    return reportData.getConfiguration();
  }

}
