package org.camunda.optimize.dto.optimize.query;

import org.camunda.optimize.dto.optimize.OptimizeDto;

import java.util.Map;

public class DurationHeatmapTargetValueDto implements OptimizeDto {

  protected String processDefinitionId;
  protected Map<String, String> targetValues;

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public void setProcessDefinitionId(String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
  }

  public Map<String, String> getTargetValues() {
    return targetValues;
  }

  public void setTargetValues(Map<String, String> targetValues) {
    this.targetValues = targetValues;
  }
}
