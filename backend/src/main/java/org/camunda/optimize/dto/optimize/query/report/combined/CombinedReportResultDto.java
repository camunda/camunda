package org.camunda.optimize.dto.optimize.query.report.combined;

import org.camunda.optimize.dto.optimize.query.report.ReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.SingleReportResultDto;

import java.util.Map;

public class CombinedReportResultDto<RESULT extends SingleReportResultDto> extends CombinedReportDefinitionDto
  implements ReportResultDto {

  protected Map<String, RESULT> result;

  public Map<String, RESULT> getResult() {
    return result;
  }

  public void setResult(Map<String, RESULT> result) {
    this.result = result;
  }
}
