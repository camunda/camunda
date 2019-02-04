package org.camunda.optimize.service.es.report.result.decision;

import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.DecisionReportMapResultDto;
import org.camunda.optimize.service.es.report.result.ReportResult;
import org.camunda.optimize.service.export.CSVUtils;

import java.util.List;
import java.util.Set;

public class SingleDecisionMapReportResult extends ReportResult<DecisionReportMapResultDto, DecisionReportDataDto> {

  public SingleDecisionMapReportResult(DecisionReportMapResultDto reportResultDto) {
    super(reportResultDto);
  }

  @Override
  public List<String[]> getResultAsCsv(final Integer limit, final Integer offset, Set<String> excludedColumns) {
    final List<String[]> csvStrings = CSVUtils.map(reportResultDto.getResult(), limit, offset);

    final String normalizedCommandKey =
      reportResultDto.getData().getView().createCommandKey().replace("-", "_");
    final String[] header =
      new String[]{reportResultDto.getData().getGroupBy().toString(), normalizedCommandKey};
    csvStrings.add(0, header);
    return csvStrings;
  }

  @Override
  public void copyReportData(DecisionReportDataDto data) {
    reportResultDto.setData(data);
  }
}
