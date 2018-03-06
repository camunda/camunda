package org.camunda.optimize.dto.optimize.query.report.result;

import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;

public abstract class ReportResultDto extends ReportDefinitionDto {

  protected long processInstanceCount;

  public long getProcessInstanceCount() {
    return processInstanceCount;
  }

  public void setProcessInstanceCount(long processInstanceCount) {
    this.processInstanceCount = processInstanceCount;
  }
}
