package org.camunda.optimize.service.es.report.result.decision;

import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.DecisionReportNumberResultDto;
import org.camunda.optimize.service.es.report.result.NumberResult;
import org.camunda.optimize.service.es.report.result.ReportEvaluationResult;

import javax.validation.constraints.NotNull;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class SingleDecisionNumberReportResult
  extends ReportEvaluationResult<DecisionReportNumberResultDto, SingleDecisionReportDefinitionDto>
  implements NumberResult {

  public SingleDecisionNumberReportResult(@NotNull final DecisionReportNumberResultDto reportResult,
                                          @NotNull final SingleDecisionReportDefinitionDto reportDefinition) {
    super(reportResult, reportDefinition);
  }

  @Override
  public List<String[]> getResultAsCsv(final Integer limit, final Integer offset, Set<String> excludedColumns) {
    final List<String[]> csvStrings = new LinkedList<>();
    csvStrings.add(new String[]{String.valueOf(reportResult.getData())});

    final String normalizedCommandKey =
      reportDefinition.getData().getView().createCommandKey().replace("-", "_");
    final String[] header = new String[]{normalizedCommandKey};
    csvStrings.add(0, header);
    return csvStrings;
  }

  @Override
  public long getResultAsNumber() {
    return reportResult.getData();
  }
}
