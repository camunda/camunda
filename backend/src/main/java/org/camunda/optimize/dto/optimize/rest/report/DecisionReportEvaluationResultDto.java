package org.camunda.optimize.dto.optimize.rest.report;

import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.DecisionReportResultDto;

public class DecisionReportEvaluationResultDto<T extends DecisionReportResultDto>
  extends EvaluationResultDto<T, SingleDecisionReportDefinitionDto> {

  public DecisionReportEvaluationResultDto() {
  }

  public DecisionReportEvaluationResultDto(final T reportResult,
                                           final SingleDecisionReportDefinitionDto reportDefinition) {
    super(reportResult, reportDefinition);
  }
}
