package org.camunda.optimize.service.alert;

import org.camunda.optimize.dto.optimize.query.alert.AlertDefinitionDto;

/**
 * @author Askar Akhmerov
 */
public class AlertJobResult {

  private boolean statusChanged;
  private AlertDefinitionDto alert;

  public AlertJobResult(AlertDefinitionDto alert) {
    this.alert = alert;
  }

  public AlertDefinitionDto getAlert() {
    return alert;
  }

  public void setAlert(AlertDefinitionDto alert) {
    this.alert = alert;
  }

  public boolean isStatusChanged() {
    return statusChanged;
  }

  public void setStatusChanged(boolean statusChanged) {
    this.statusChanged = statusChanged;
  }
}
