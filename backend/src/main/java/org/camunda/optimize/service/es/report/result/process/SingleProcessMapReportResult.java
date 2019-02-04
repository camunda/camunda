package org.camunda.optimize.service.es.report.result.process;

import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportMapResultDto;
import org.camunda.optimize.service.es.report.result.ReportResult;
import org.camunda.optimize.service.export.CSVUtils;

import java.util.List;
import java.util.Set;

public class SingleProcessMapReportResult extends ReportResult<ProcessReportMapResultDto, ProcessReportDataDto> {

  public SingleProcessMapReportResult(ProcessReportMapResultDto reportResultDto) {
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
  public void copyReportData(ProcessReportDataDto data) {
    reportResultDto.setData(data);
  }
}
