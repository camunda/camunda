package org.camunda.optimize.service.es.report.result.process;

import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportMapResultDto;
import org.camunda.optimize.service.es.report.result.ReportEvaluationResult;
import org.camunda.optimize.service.export.CSVUtils;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Set;

public class SingleProcessMapReportResult
  extends ReportEvaluationResult<ProcessReportMapResultDto, SingleProcessReportDefinitionDto> {

  public SingleProcessMapReportResult(@NotNull final ProcessReportMapResultDto reportResult,
                                      @NotNull final SingleProcessReportDefinitionDto reportDefinition) {
    super(reportResult, reportDefinition);
  }

  @Override
  public List<String[]> getResultAsCsv(final Integer limit, final Integer offset, Set<String> excludedColumns) {
    final List<String[]> csvStrings = CSVUtils.map(reportResult.getData(), limit, offset);

    final String normalizedCommandKey =
      reportDefinition.getData().getView().createCommandKey().replace("-", "_");
    final String[] header =
      new String[]{reportDefinition.getData().getGroupBy().toString(), normalizedCommandKey};
    csvStrings.add(0, header);
    return csvStrings;
  }

}
