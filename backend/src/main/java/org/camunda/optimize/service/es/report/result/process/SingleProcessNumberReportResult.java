package org.camunda.optimize.service.es.report.result.process;

import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportNumberResultDto;
import org.camunda.optimize.service.es.report.result.ReportResult;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class SingleProcessNumberReportResult extends ReportResult<ProcessReportNumberResultDto, ProcessReportDataDto> {

  public SingleProcessNumberReportResult(ProcessReportNumberResultDto reportResultDto) {
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
  public void copyReportData(ProcessReportDataDto processReportDataDto) {
    reportResultDto.setData(processReportDataDto);
  }


}
