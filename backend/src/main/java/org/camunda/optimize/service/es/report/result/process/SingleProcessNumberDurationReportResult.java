package org.camunda.optimize.service.es.report.result.process;

import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.duration.AggregationResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.duration.ProcessDurationReportNumberResultDto;
import org.camunda.optimize.service.es.report.result.NumberResult;
import org.camunda.optimize.service.es.report.result.ReportEvaluationResult;

import javax.validation.constraints.NotNull;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class SingleProcessNumberDurationReportResult
  extends ReportEvaluationResult<ProcessDurationReportNumberResultDto, SingleProcessReportDefinitionDto>
  implements NumberResult {

  public SingleProcessNumberDurationReportResult(@NotNull final ProcessDurationReportNumberResultDto reportResult,
                                                 @NotNull final SingleProcessReportDefinitionDto reportDefinition) {
    super(reportResult, reportDefinition);
  }

  @Override
  public List<String[]> getResultAsCsv(final Integer limit, final Integer offset, Set<String> excludedColumns) {
    final List<String[]> csvStrings = new LinkedList<>();
    AggregationResultDto result = reportResult.getData();
    csvStrings.add(
      new String[]{
        result.getMin().toString(),
        result.getMax().toString(),
        result.getAvg().toString(),
        result.getMedian().toString()
      });

    final String normalizedCommandKey =
      reportDefinition.getData().getView().createCommandKey().replace("-", "_");

    final String[] operations =
      new String[]{"minimum", "maximum", "average", "median"};
    csvStrings.add(0, operations);
    final String[] header =
      new String[]{normalizedCommandKey, "", "", ""};
    csvStrings.add(0, header);
    return csvStrings;
  }

  @Override
  public long getResultAsNumber() {
    AggregationType aggregationType = reportDefinition.getData().getConfiguration().getAggregationType();
    return reportResult.getData().getResultForGivenAggregationType(aggregationType);
  }
}
