package org.camunda.optimize.dto.optimize.query.report.result;

import java.util.HashMap;
import java.util.Map;

public class MapReportResultDto extends ReportResultDto {

  private Map<String, Long> result = new HashMap<>();

  public Map<String, Long> getResult() {
    return result;
  }

  public void setResult(Map<String, Long> result) {
    this.result = result;
  }
}
