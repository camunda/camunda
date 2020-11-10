/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.result.process;

import org.camunda.optimize.dto.optimize.query.report.ReportEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.service.export.CSVUtils;

import javax.validation.constraints.NotNull;
import java.time.ZoneId;
import java.util.List;

public class SingleProcessMapReportResult
  extends ReportEvaluationResult<ReportMapResultDto, SingleProcessReportDefinitionRequestDto> {

  public SingleProcessMapReportResult(@NotNull final ReportMapResultDto reportResult,
                                      @NotNull final SingleProcessReportDefinitionRequestDto reportDefinition) {
    super(reportResult, reportDefinition);
  }

  @Override
  public List<String[]> getResultAsCsv(final Integer limit, final Integer offset, final ZoneId timezone) {
    final List<String[]> csvStrings = CSVUtils.map(reportResult.getData(), limit, offset);
    addCsvHeader(csvStrings);
    return csvStrings;
  }

  public void addCsvHeader(final List<String[]> csvStrings) {
    if (reportDefinition.getData().isFrequencyReport()) {
      addFrequencyHeader(csvStrings);
    } else {
      addDurationHeader(csvStrings);
    }
  }

  private void addDurationHeader(final List<String[]> csvStrings) {
    final ProcessReportDataDto reportData = reportDefinition.getData();
    final String normalizedCommandKey =
      reportData.getView().createCommandKey().replace("-", "_");
    final String[] operations =
      new String[]{"", CSVUtils.mapAggregationType(reportData.getConfiguration().getAggregationType())};
    csvStrings.add(0, operations);
    final String[] header =
      new String[]{reportData.getGroupBy().toString(), normalizedCommandKey};
    csvStrings.add(0, header);
  }

  private void addFrequencyHeader(final List<String[]> csvStrings) {
    final String normalizedCommandKey =
      reportDefinition.getData().getView().createCommandKey().replace("-", "_");
    csvStrings.add(0, new String[]{reportDefinition.getData().getGroupBy().toString(), normalizedCommandKey});
  }

}
