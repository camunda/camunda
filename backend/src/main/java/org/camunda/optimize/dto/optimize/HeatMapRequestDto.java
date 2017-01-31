package org.camunda.optimize.dto.optimize;

import java.io.Serializable;
import java.util.List;

/**
 * @author Askar Akhmerov
 */
public class HeatMapRequestDto implements Serializable {
  protected String processDefinitionId;
  private List<String> correlationActivities;

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public void setProcessDefinitionId(String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
  }

  public List<String> getCorrelationActivities() {
    return correlationActivities;
  }

  public void setCorrelationActivities(List<String> correlationActivities) {
    this.correlationActivities = correlationActivities;
  }
}
