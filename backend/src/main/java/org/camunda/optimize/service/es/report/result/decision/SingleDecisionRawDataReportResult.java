package org.camunda.optimize.service.es.report.result.decision;

import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionReportResultDto;
import org.camunda.optimize.service.es.report.result.ReportResult;
import org.camunda.optimize.service.export.CSVUtils;

import java.util.List;
import java.util.Set;

public class SingleDecisionRawDataReportResult extends ReportResult<RawDataDecisionReportResultDto, DecisionReportDataDto> {

  public SingleDecisionRawDataReportResult(RawDataDecisionReportResultDto reportResultDto) {
    super(reportResultDto);
  }

  @Override
  public List<String[]> getResultAsCsv(final Integer limit, final Integer offset, Set<String> excludedColumns) {
    List<RawDataDecisionInstanceDto> rawData = reportResultDto.getResult();
    return CSVUtils.mapRawDecisionReportInstances(rawData, limit, offset, excludedColumns);
  }

  @Override
  public void copyReportData(DecisionReportDataDto data) {
    reportResultDto.setData(data);
  }
}
