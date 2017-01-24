package org.camunda.optimize.dto.optimize;

import java.io.Serializable;
import java.util.List;

/**
 * @author Askar Akhmerov
 */
public class HeatMapRequestDto implements Serializable {
  protected String key;
  private List<String> correlationActivities;

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public List<String> getCorrelationActivities() {
    return correlationActivities;
  }

  public void setCorrelationActivities(List<String> correlationActivities) {
    this.correlationActivities = correlationActivities;
  }
}
