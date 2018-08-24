package org.camunda.optimize.dto.optimize.query.report.single.result;

import org.camunda.optimize.dto.optimize.query.report.ReportResultDto;
import org.camunda.optimize.dto.optimize.query.report.single.SingleReportDefinitionDto;

public abstract class SingleReportResultDto extends SingleReportDefinitionDto implements ReportResultDto {

  protected long processInstanceCount;

  public long getProcessInstanceCount() {
    return processInstanceCount;
  }

  public void setProcessInstanceCount(long processInstanceCount) {
    this.processInstanceCount = processInstanceCount;
  }
}
