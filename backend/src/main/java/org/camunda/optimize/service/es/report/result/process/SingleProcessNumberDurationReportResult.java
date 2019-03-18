package org.camunda.optimize.service.es.report.result.process;

import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.duration.OperationResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.duration.ProcessDurationReportNumberResultDto;
import org.camunda.optimize.service.es.report.result.ReportResult;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class SingleProcessNumberDurationReportResult
  extends ReportResult<ProcessDurationReportNumberResultDto, ProcessReportDataDto> implements NumberResult {

  public SingleProcessNumberDurationReportResult(ProcessDurationReportNumberResultDto reportResultDto) {
    super(reportResultDto);
  }

  @Override
  public List<String[]> getResultAsCsv(final Integer limit, final Integer offset, Set<String> excludedColumns) {
    final List<String[]> csvStrings = new LinkedList<>();
    OperationResultDto result = reportResultDto.getResult();
    csvStrings.add(
      new String[]{
        result.getMin().toString(),
        result.getMax().toString(),
        result.getAvg().toString(),
        result.getMedian().toString()
      });

    final String normalizedCommandKey =
      reportResultDto.getData().getView().createCommandKey().replace("-", "_");

    final String[] operations =
      new String[]{"minimum", "maximum", "average", "median"};
    csvStrings.add(0, operations);
    final String[] header =
      new String[]{normalizedCommandKey};
    csvStrings.add(0, header);
    return csvStrings;
  }

  @Override
  public void copyReportData(ProcessReportDataDto processReportDataDto) {
    reportResultDto.setData(processReportDataDto);
  }

  @Override
  public long getResultAsNumber() {
    return reportResultDto.getResult().getAvg();
  }
}
