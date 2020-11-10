/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.result.process;

import org.camunda.optimize.dto.optimize.query.report.ReportEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.service.export.CSVUtils;

import javax.validation.constraints.NotNull;
import java.time.ZoneId;
import java.util.List;

import static org.camunda.optimize.service.security.util.LocalDateUtil.atSameTimezoneOffsetDateTime;

public class SingleProcessRawDataReportResult
  extends ReportEvaluationResult<RawDataProcessReportResultDto, SingleProcessReportDefinitionRequestDto> {

  public SingleProcessRawDataReportResult(@NotNull final RawDataProcessReportResultDto reportResult,
                                          @NotNull final SingleProcessReportDefinitionRequestDto reportDefinition) {
    super(reportResult, reportDefinition);
  }

  @Override
  public List<String[]> getResultAsCsv(final Integer limit, final Integer offset, final ZoneId timezone) {
    List<RawDataProcessInstanceDto> rawData = reportResult.getData();
    rawData.forEach(raw -> {
      raw.setStartDate(atSameTimezoneOffsetDateTime(raw.getStartDate(), timezone));
      raw.setEndDate(atSameTimezoneOffsetDateTime(raw.getEndDate(), timezone));
    });
    return CSVUtils.mapRawProcessReportInstances(
      rawData,
      limit,
      offset,
      reportDefinition.getData().getConfiguration().getTableColumns()
    );
  }

}
