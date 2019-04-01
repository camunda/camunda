package org.camunda.optimize.dto.optimize.rest.report;

import org.camunda.optimize.dto.optimize.query.report.ReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ResultType;

import java.util.Map;

public class CombinedProcessReportResultDataDto<RESULT extends ProcessReportResultDto> implements ReportResultDto {
  protected Map<String, ProcessReportEvaluationResultDto<RESULT>> data;

  protected CombinedProcessReportResultDataDto() {
  }

  public CombinedProcessReportResultDataDto(final Map<String, ProcessReportEvaluationResultDto<RESULT>> data) {
    this.data = data;
  }

  public Map<String, ProcessReportEvaluationResultDto<RESULT>> getData() {
    return data;
  }

  public void setData(Map<String, ProcessReportEvaluationResultDto<RESULT>> data) {
    this.data = data;
  }

  @Override
  public ResultType getType() {
    // type is in single results
    return null;
  }
}
