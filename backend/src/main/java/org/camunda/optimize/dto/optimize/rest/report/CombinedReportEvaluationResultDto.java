package org.camunda.optimize.dto.optimize.rest.report;

import org.camunda.optimize.dto.optimize.query.report.combined.CombinedReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportResultDto;

public class CombinedReportEvaluationResultDto<T extends ProcessReportResultDto>
  extends EvaluationResultDto<CombinedProcessReportResultDataDto<T>, CombinedReportDefinitionDto> {

  public CombinedReportEvaluationResultDto() {
  }

  public CombinedReportEvaluationResultDto(final CombinedProcessReportResultDataDto<T> reportResult,
                                           final CombinedReportDefinitionDto reportDefinition) {
    super(reportResult, reportDefinition);
  }
}
