/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.result.decision;

import org.camunda.optimize.dto.optimize.query.report.ReportEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.NumberResultDto;
import org.camunda.optimize.service.es.report.result.NumberResult;

import javax.validation.constraints.NotNull;
import java.time.ZoneId;
import java.util.LinkedList;
import java.util.List;

public class SingleDecisionNumberReportResult
  extends ReportEvaluationResult<NumberResultDto, SingleDecisionReportDefinitionRequestDto>
  implements NumberResult {

  public SingleDecisionNumberReportResult(@NotNull final NumberResultDto reportResult,
                                          @NotNull final SingleDecisionReportDefinitionRequestDto reportDefinition) {
    super(reportResult, reportDefinition);
  }

  @Override
  public List<String[]> getResultAsCsv(final Integer limit, final Integer offset, final ZoneId timezone) {
    final List<String[]> csvStrings = new LinkedList<>();
    csvStrings.add(new String[]{String.valueOf(reportResult.getData())});

    final String normalizedCommandKey =
      reportDefinition.getData().getView().createCommandKey().replace("-", "_");
    final String[] header = new String[]{normalizedCommandKey};
    csvStrings.add(0, header);
    return csvStrings;
  }

  @Override
  public Double getResultAsNumber() {
    return reportResult.getData();
  }
}
