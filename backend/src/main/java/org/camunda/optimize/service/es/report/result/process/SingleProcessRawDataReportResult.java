package org.camunda.optimize.service.es.report.result.process;

import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.service.es.report.result.ReportResult;
import org.camunda.optimize.service.export.CSVUtils;

import java.util.List;
import java.util.Set;

public class SingleProcessRawDataReportResult extends ReportResult<RawDataProcessReportResultDto, ProcessReportDataDto> {

  public SingleProcessRawDataReportResult(RawDataProcessReportResultDto reportResultDto) {
    super(reportResultDto);
  }

  @Override
  public List<String[]> getResultAsCsv(final Integer limit, final Integer offset, Set<String> excludedColumns) {
    List<RawDataProcessInstanceDto> rawData = reportResultDto.getResult();
    return CSVUtils.mapRawProcessReportInstances(rawData, limit, offset, excludedColumns);
  }

  @Override
  public void copyReportData(ProcessReportDataDto processReportDataDto) {
    reportResultDto.setData(processReportDataDto);
  }


}
