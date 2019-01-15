package org.camunda.optimize.dto.optimize.query.report.combined;

import org.camunda.optimize.dto.optimize.query.report.ReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportResultDto;

import java.util.Map;

public class CombinedProcessReportResultDto<RESULT extends ProcessReportResultDto>
  extends CombinedReportDefinitionDto implements ReportResultDto {

  protected Map<String, RESULT> result;

  public Map<String, RESULT> getResult() {
    return result;
  }

  public void setResult(Map<String, RESULT> result) {
    this.result = result;
  }
}
