package org.camunda.optimize.service.es.report.result.decision;

import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.DecisionReportNumberResultDto;
import org.camunda.optimize.service.es.report.result.NumberResult;
import org.camunda.optimize.service.es.report.result.ReportResult;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class SingleDecisionNumberReportResult
  extends ReportResult<DecisionReportNumberResultDto, DecisionReportDataDto> implements NumberResult {

  public SingleDecisionNumberReportResult(DecisionReportNumberResultDto reportResultDto) {
    super(reportResultDto);
  }

  @Override
  public List<String[]> getResultAsCsv(final Integer limit, final Integer offset, Set<String> excludedColumns) {
    final List<String[]> csvStrings = new LinkedList<>();
    csvStrings.add(new String[]{String.valueOf(reportResultDto.getResult())});

    final String normalizedCommandKey =
      reportResultDto.getData().getView().createCommandKey().replace("-", "_");
    final String[] header = new String[]{normalizedCommandKey};
    csvStrings.add(0, header);
    return csvStrings;
  }

  @Override
  public void copyReportData(DecisionReportDataDto data) {
    reportResultDto.setData(data);
  }

  @Override
  public long getResultAsNumber() {
    return reportResultDto.getResult();
  }
}
