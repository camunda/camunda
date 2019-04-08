/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.result.process;

import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.service.es.report.result.ReportEvaluationResult;
import org.camunda.optimize.service.export.CSVUtils;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Set;

public class SingleProcessRawDataReportResult
  extends ReportEvaluationResult<RawDataProcessReportResultDto, SingleProcessReportDefinitionDto> {

  public SingleProcessRawDataReportResult(@NotNull final RawDataProcessReportResultDto reportResult,
                                          @NotNull final SingleProcessReportDefinitionDto reportDefinition) {
    super(reportResult, reportDefinition);
  }

  @Override
  public List<String[]> getResultAsCsv(final Integer limit, final Integer offset, Set<String> excludedColumns) {
    List<RawDataProcessInstanceDto> rawData = reportResult.getData();
    return CSVUtils.mapRawProcessReportInstances(rawData, limit, offset, excludedColumns);
  }

}
