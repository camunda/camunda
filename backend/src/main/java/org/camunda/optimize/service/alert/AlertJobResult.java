package org.camunda.optimize.service.alert;

/**
 * @author Askar Akhmerov
 */
public class AlertJobResult {

  private boolean statusChanged;
  private boolean triggered;

  public boolean isStatusChanged() {
    return statusChanged;
  }

  public void setStatusChanged(boolean statusChanged) {
    this.statusChanged = statusChanged;
  }

  public boolean isTriggered() {
    return triggered;
  }

  public void setTriggered(boolean triggered) {
    this.triggered = triggered;
  }
}
