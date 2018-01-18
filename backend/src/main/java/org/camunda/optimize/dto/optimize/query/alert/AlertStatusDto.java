package org.camunda.optimize.dto.optimize.query.alert;

/**
 * @author Askar Akhmerov
 */
public class AlertStatusDto {

  //matches with AlertDefinitionDto id
  private final String id;
  private boolean triggered;

  public AlertStatusDto() {
    this(null);
  }

  public AlertStatusDto(String alertId) {
    this.id = alertId;
  }

  public String getId() {
    return id;
  }

  public boolean isTriggered() {
    return triggered;
  }

  public void setTriggered(boolean triggered) {
    this.triggered = triggered;
  }
}
